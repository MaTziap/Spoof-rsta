package com.example.proxy

import android.util.Log
import com.example.data.entity.ActivityLogEntity
import com.example.data.entity.SettingsEntity
import com.example.data.repository.ProxyRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

object DpiBypassProxyServer {
    private const val TAG = "DpiProxyServer"
    private const val BUFFER_SIZE = 131072
    private val connCounter = AtomicLong(0)

    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private val proxyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val poolExecutor = Executors.newFixedThreadPool(64)

    private val _serviceState = MutableStateFlow(false)
    val serviceState = _serviceState.asStateFlow()

    private val _totalConns = MutableStateFlow(0L)
    val totalConns = _totalConns.asStateFlow()

    private val _totalC2S = MutableStateFlow(0L)
    val totalC2S = _totalC2S.asStateFlow()

    private val _totalS2C = MutableStateFlow(0L)
    val totalS2C = _totalS2C.asStateFlow()

    fun start(repository: ProxyRepository, settings: SettingsEntity) {
        if (_serviceState.value) return
        Log.d(TAG, "Starting proxy server on port ${settings.listenPort} to target ${settings.connectIp}:${settings.connectPort}")
        _serviceState.value = true

        proxyJob = proxyScope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(settings.listenHost, settings.listenPort))
                }
                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.tcpNoDelay = true
                    clientSocket.receiveBufferSize = 524288
                    clientSocket.sendBufferSize = 524288
                    
                    poolExecutor.submit {
                        runBlocking {
                            handleClientConnection(clientSocket, settings, repository)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket exception: ${e.message}")
            } finally {
                _serviceState.value = false
            }
        }
    }

    fun stop() {
        if (!_serviceState.value) return
        Log.d(TAG, "Stopping proxy server...")
        _serviceState.value = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null
        proxyJob?.cancel()
        proxyJob = null
    }

    private suspend fun handleClientConnection(
        clientSocket: Socket,
        settings: SettingsEntity,
        repository: ProxyRepository
    ) {
        val cid = "C${String.format("%06d", connCounter.getAndIncrement())}"
        _totalConns.value = connCounter.get()

        val clientAddr = clientSocket.remoteSocketAddress?.toString() ?: "unknown"
        val serverAddr = "${settings.connectIp}:${settings.connectPort}"
        val startTime = System.currentTimeMillis()

        // Create log entity in Room DB
        val logEntity = ActivityLogEntity(
            connId = cid,
            clientAddr = clientAddr,
            serverAddr = serverAddr,
            fakeSni = settings.fakeSni,
            method = settings.bypassMethod,
            status = "connecting",
            timestamp = startTime
        )
        val logDbId = repository.insertLog(logEntity)

        var finalLogEntity = logEntity.copy(id = logDbId)
        var serverSocket: Socket? = null

        try {
            clientSocket.soTimeout = 10000 // 10 seconds to read ClientHello
            val clientInput = clientSocket.getInputStream()
            val clientOutput = clientSocket.getOutputStream()

            val readBuf = ByteArray(BUFFER_SIZE)
            val bytesRead = clientInput.read(readBuf)
            if (bytesRead <= 0) {
                updateLogStatus(repository, finalLogEntity, "closed", "No ClientHello payload read.")
                clientSocket.safeClose()
                return
            }

            val rawHello = ByteArray(bytesRead)
            System.arraycopy(readBuf, 0, rawHello, 0, bytesRead)
            clientSocket.soTimeout = 0 // Reset read timeout

            // Parse real SNI domain
            val parsedSni = ClientHelloParser.parseSNI(rawHello) ?: ""
            Log.d(TAG, "[$cid] Extracted SNI: '$parsedSni'")
            
            // Connect to Target Server
            serverSocket = Socket()
            serverSocket.tcpNoDelay = true
            serverSocket.receiveBufferSize = 524288
            serverSocket.sendBufferSize = 524288
            serverSocket.connect(InetSocketAddress(settings.connectIp, settings.connectPort), 15000)

            val serverOutput = serverSocket.getOutputStream()
            val serverInput = serverSocket.getInputStream()

            // Update log with parsed SNI domain
            finalLogEntity = finalLogEntity.copy(realSni = parsedSni)
            repository.updateLog(finalLogEntity)

            // Apply Bypass Strategy
            val appliedSuccessfully = applyBypassStrategyFlow(
                cid,
                settings,
                rawHello,
                serverOutput
            )

            if (!appliedSuccessfully) {
                // Fallback to writing raw Hello
                serverOutput.write(rawHello)
                serverOutput.flush()
            }

            // BIDIRECTIONAL PIPE FORWARDING
            val serverReplied = handleDuplexForwarding(
                cid,
                clientSocket,
                serverSocket,
                clientInput,
                clientOutput,
                serverInput,
                serverOutput,
                finalLogEntity,
                repository,
                startTime
            )

            val durationSecs = (System.currentTimeMillis() - startTime) / 1000.0
            finalLogEntity = finalLogEntity.copy(
                status = "closed",
                serverReplied = serverReplied,
                duration = durationSecs
            )
            repository.updateLog(finalLogEntity)

        } catch (e: Exception) {
            val durationSecs = (System.currentTimeMillis() - startTime) / 1000.0
            Log.e(TAG, "[$cid] Connection Error: ${e.message}")
            finalLogEntity = finalLogEntity.copy(
                status = "error",
                errorMessage = e.message ?: "Unknown Exception",
                duration = durationSecs
            )
            repository.updateLog(finalLogEntity)
        } finally {
            clientSocket.safeClose()
            serverSocket?.safeClose()
        }
    }

    private suspend fun updateLogStatus(
        repository: ProxyRepository,
        entity: ActivityLogEntity,
        status: String,
        errMsg: String? = null
    ) {
        repository.updateLog(
            entity.copy(
                status = status,
                errorMessage = errMsg,
                duration = (System.currentTimeMillis() - entity.timestamp) / 1000.0
            )
        )
    }

    /**
     * Applies selected bypass methods. Truly matches the target behavior.
     */
    private suspend fun applyBypassStrategyFlow(
        cid: String,
        settings: SettingsEntity,
        rawHello: ByteArray,
        serverOutput: OutputStream
    ): Boolean {
        return try {
            when (settings.bypassMethod.lowercase()) {
                "fragment" -> {
                    val fragments = fragmentClientHello(rawHello, settings.fragmentStrategy)
                    for (i in fragments.indices) {
                        serverOutput.write(fragments[i])
                        serverOutput.flush()
                        if (i < fragments.size - 1 && settings.fragmentDelay > 0) {
                            delay((settings.fragmentDelay * 1000).toLong())
                        }
                    }
                    true
                }
                "fake_sni" -> {
                    if (settings.useTtlTrick) {
                        sendTtlProbe(settings)
                    } else {
                        // Standard fake Hello prefix send
                        try {
                            val fakeHello = ClientHelloBuilder.buildClientHello(settings.fakeSni, targetSize = 517)
                            serverOutput.write(fakeHello)
                            serverOutput.flush()
                            delay(50)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                    // Write real Hello split with sni_split
                    val fragments = fragmentClientHello(rawHello, "sni_split")
                    for (i in fragments.indices) {
                        serverOutput.write(fragments[i])
                        serverOutput.flush()
                        if (i < fragments.size - 1) {
                            delay(100)
                        }
                    }
                    true
                }
                "combined" -> {
                    // Combine low-ttl fake SNI probe + custom fragmentation
                    if (settings.useTtlTrick) {
                        sendTtlProbe(settings)
                    } else {
                        // Send fake hello inline
                        try {
                            val fakeHello = ClientHelloBuilder.buildClientHello(settings.fakeSni, targetSize = 517)
                            serverOutput.write(fakeHello)
                            serverOutput.flush()
                            delay(50)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                    // Fragment with settings choice
                    val fragments = fragmentClientHello(rawHello, settings.fragmentStrategy)
                    for (i in fragments.indices) {
                        serverOutput.write(fragments[i])
                        serverOutput.flush()
                        if (i < fragments.size - 1 && settings.fragmentDelay > 0) {
                            delay((settings.fragmentDelay * 1000).toLong())
                        }
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$cid] Error applying bypass: ${e.message}")
            false
        }
    }

    private fun sendTtlProbe(settings: SettingsEntity) {
        try {
            val fakeHello = ClientHelloBuilder.buildClientHello(settings.fakeSni, targetSize = 517)
            val probe = Socket()
            // Dial target server with extremely short timeout (300 ms TTL mimic)
            probe.connect(InetSocketAddress(settings.connectIp, settings.connectPort), 300)
            probe.soTimeout = 300
            probe.getOutputStream().write(fakeHello)
            probe.getOutputStream().flush()
            probe.close()
            Thread.sleep(50)
        } catch (e: Exception) {
            // Intended timeout or reset
        }
    }

    private fun fragmentClientHello(data: ByteArray, strategy: String): List<ByteArray> {
        if (strategy == "none" || data.size < 10) {
            return listOf(data)
        }
        return when (strategy.lowercase()) {
            "sni_split" -> {
                val offsetPair = ClientHelloParser.findSNIOffset(data)
                if (offsetPair == null) {
                    val mid = data.size / 2
                    listOf(data.copyOfRange(0, mid), data.copyOfRange(mid, data.size))
                } else {
                    val sniOffset = offsetPair.first
                    val sniLen = offsetPair.second
                    val splitPoint = sniOffset + (sniLen / 2)
                    listOf(data.copyOfRange(0, splitPoint), data.copyOfRange(splitPoint, data.size))
                }
            }
            "half" -> {
                val mid = data.size / 2
                listOf(data.copyOfRange(0, mid), data.copyOfRange(mid, data.size))
            }
            "multi" -> {
                val result = mutableListOf<ByteArray>()
                var i = 0
                val chunkSize = 24
                while (i < data.size) {
                    val end = if (i + chunkSize > data.size) data.size else i + chunkSize
                    result.add(data.copyOfRange(i, end))
                    i += chunkSize
                }
                result
            }
            "tls_record_frag" -> {
                if (data.size < 6 || data[0] != 0x16.toByte()) {
                    listOf(data)
                } else {
                    val recordVersion = data.copyOfRange(1, 3)
                    val handshakeData = data.copyOfRange(5, data.size)
                    val mid = handshakeData.size / 2
                    val part1 = handshakeData.copyOfRange(0, mid)
                    val part2 = handshakeData.copyOfRange(mid, handshakeData.size)

                    val record1 = ByteArray(5 + part1.size)
                    record1[0] = 0x16.toByte()
                    System.arraycopy(recordVersion, 0, record1, 1, 2)
                    record1[3] = ((part1.size shr 8) and 0xFF).toByte()
                    record1[4] = (part1.size and 0xFF).toByte()
                    System.arraycopy(part1, 0, record1, 5, part1.size)

                    val record2 = ByteArray(5 + part2.size)
                    record2[0] = 0x16.toByte()
                    System.arraycopy(recordVersion, 0, record2, 1, 2)
                    record2[3] = ((part2.size shr 8) and 0xFF).toByte()
                    record2[4] = (part2.size and 0xFF).toByte()
                    System.arraycopy(part2, 0, record2, 5, part2.size)

                    listOf(record1, record2)
                }
            }
            else -> listOf(data)
        }
    }

    private suspend fun handleDuplexForwarding(
        cid: String,
        clientSocket: Socket,
        serverSocket: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream,
        serverInput: InputStream,
        serverOutput: OutputStream,
        initialLogEntity: ActivityLogEntity,
        repository: ProxyRepository,
        startTime: Long
    ): Boolean {
        var serverReplied = false
        var currentLog = initialLogEntity
        val c2sAcc = AtomicLong(0)
        val s2cAcc = AtomicLong(0)

        coroutineScope {
            // Forward Client -> Server (Upload)
            val clientTask = launch(Dispatchers.IO) {
                val buffer = ByteArray(BUFFER_SIZE)
                try {
                    while (isActive) {
                        val limit = clientInput.read(buffer)
                        if (limit <= 0) break
                        serverOutput.write(buffer, 0, limit)
                        serverOutput.flush()

                        val uploaded = c2sAcc.addAndGet(limit.toLong())
                        _totalC2S.value = _totalC2S.value + limit

                        // Update DB log occasionally to keep state and byte counters synced
                        if (uploaded % 65536 < limit) {
                            currentLog = currentLog.copy(
                                bytesC2S = uploaded,
                                status = "active",
                                duration = (System.currentTimeMillis() - startTime) / 1000.0
                            )
                            repository.updateLog(currentLog)
                        }
                    }
                } catch (e: Exception) {
                    // Connection closed
                } finally {
                    try { serverSocket.shutdownOutput() } catch (e: Exception) {}
                }
            }

            // Forward Server -> Client (Download)
            val serverTask = launch(Dispatchers.IO) {
                val buffer = ByteArray(BUFFER_SIZE)
                try {
                    while (isActive) {
                        val limit = serverInput.read(buffer)
                        if (limit <= 0) break
                        clientOutput.write(buffer, 0, limit)
                        clientOutput.flush()

                        if (!serverReplied) {
                            serverReplied = true
                            currentLog = currentLog.copy(serverReplied = true, status = "active")
                            repository.updateLog(currentLog)
                        }

                        val downloaded = s2cAcc.addAndGet(limit.toLong())
                        _totalS2C.value = _totalS2C.value + limit

                        if (downloaded % 65536 < limit) {
                            currentLog = currentLog.copy(
                                bytesS2C = downloaded,
                                status = "active",
                                duration = (System.currentTimeMillis() - startTime) / 1000.0
                            )
                            repository.updateLog(currentLog)
                        }
                    }
                } catch (e: Exception) {
                    // Connection closed
                } finally {
                    try { clientSocket.shutdownOutput() } catch (e: Exception) {}
                }
            }

            // Wait until client finish reading (pipe ends)
            joinAll(clientTask, serverTask)
        }

        // Final bytes check update
        currentLog = currentLog.copy(bytesC2S = c2sAcc.get(), bytesS2C = s2cAcc.get())
        repository.updateLog(currentLog)

        return serverReplied
    }

    private fun Socket.safeClose() {
        try { close() } catch (e: Exception) {}
    }
}
