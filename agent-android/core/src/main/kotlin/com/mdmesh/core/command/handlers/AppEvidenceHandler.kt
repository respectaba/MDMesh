package com.mdmesh.core.command.handlers

import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.core.device.InstallationEvidenceCollector
import com.mdmesh.proto.AppManagement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import com.mdmesh.proto.AppEvidenceRequest
import com.mdmesh.proto.AppEvidenceResult
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.ProtocolJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.decodeFromJsonElement

class AppEvidenceHandler(private val collector: InstallationEvidenceCollector, private val isDeviceOwner: () -> Boolean) : CommandHandler {
    override val type = "apps.evidence"
    override suspend fun handle(command: CommandEnvelope): CommandResult = withContext(Dispatchers.IO) {
        fun permitted() = runCatching { isDeviceOwner() }.getOrDefault(false)
        if (command.requiresCapability != "app.${AppManagement.INSTALLATION_EVIDENCE}" || !permitted())
            return@withContext CommandResults.unsupported(command)
        val job = currentCoroutineContext()[Job]
        val request = runCatching {
            ProtocolJson.json.decodeFromJsonElement<AppEvidenceRequest>(requireNotNull(command.payload))
        }.getOrElse { return@withContext CommandResults.failed(command, "Invalid installation evidence request") }
        val result = runCatching { AppEvidenceResult(collector.collect(request.packages) { job?.isActive != false && permitted() }) }
            .getOrElse { if (it is CancellationException) throw it; return@withContext CommandResults.failed(command, "Installation evidence unavailable") }
        val json = ProtocolJson.json.encodeToString(AppEvidenceResult.serializer(), result)
        if (!permitted()) return@withContext CommandResults.unsupported(command)
        CommandResults.done(command, json)
    }
}
