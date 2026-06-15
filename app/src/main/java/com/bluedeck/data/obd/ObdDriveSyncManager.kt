package com.bluedeck.data.obd

import android.accounts.Account
import android.content.Context
import com.bluedeck.R
import com.bluedeck.data.obd.db.ObdDatabase
import com.bluedeck.data.repository.PreferencesManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObdDriveSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ObdDatabase,
    private val preferencesManager: PreferencesManager,
    private val csvExporter: ObdCsvExporter
) {

    fun isConfigured(): Boolean {
        val clientId = context.getString(R.string.google_drive_web_client_id)
        return clientId.isNotBlank() && clientId != "YOUR_WEB_CLIENT_ID"
    }

    fun buildSignInClient() = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .apply {
                val clientId = context.getString(R.string.google_drive_web_client_id)
                if (clientId.isNotBlank() && clientId != "YOUR_WEB_CLIENT_ID") {
                    requestIdToken(clientId)
                    requestScopes(Scope(DriveScopes.DRIVE_FILE))
                }
            }
            .build()
    )

    suspend fun getSignedInAccountEmail(): String? = withContext(Dispatchers.IO) {
        GoogleSignIn.getLastSignedInAccount(context)?.email
    }

    suspend fun syncSession(sessionId: Long): Result<String> = withContext(Dispatchers.IO) {
        if (!preferencesManager.obdDriveSyncEnabled.first()) {
            return@withContext Result.failure(IllegalStateException("Drive sync disabled"))
        }
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Google Drive not configured"))
        }
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext Result.failure(IllegalStateException("Not signed in to Google"))
        val session = database.sessionDao().getById(sessionId)
            ?: return@withContext Result.failure(IllegalStateException("Session not found"))
        if (!session.driveFileId.isNullOrBlank()) {
            return@withContext Result.success(session.driveFileId)
        }

        val samples = database.sampleDao().getForSession(sessionId)
        val csv = csvExporter.buildSessionCsv(session, samples)
        val drive = buildDriveService(
            account.account ?: return@withContext Result.failure(IllegalStateException("No account"))
        )

        val folderId = findOrCreateFolder(drive)
        val metadata = File().apply {
            name = csvExporter.sessionFileName(session)
            parents = listOf(folderId)
            mimeType = "text/csv"
        }
        val uploaded = drive.files().create(
            metadata,
            ByteArrayContent("text/csv", csv.toByteArray(Charsets.UTF_8))
        ).setFields("id").execute()

        val fileId = uploaded.id
        database.sessionDao().update(session.copy(driveFileId = fileId))
        preferencesManager.setObdDriveLastSyncAt(System.currentTimeMillis())
        Result.success(fileId)
    }

    private fun buildDriveService(account: Account): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(context.getString(R.string.app_name))
            .build()
    }

    private fun findOrCreateFolder(drive: Drive): String {
        val query = "mimeType='application/vnd.google-apps.folder' and name='OBD Logs' and trashed=false"
        val existing = drive.files().list().setQ(query).setSpaces("drive").setFields("files(id)").execute()
        existing.files?.firstOrNull()?.id?.let { return it }

        val bluedeckFolder = drive.files().list()
            .setQ("mimeType='application/vnd.google-apps.folder' and name='BlueDeck' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
            .files?.firstOrNull()?.id

        val metadata = File().apply {
            name = "OBD Logs"
            mimeType = "application/vnd.google-apps.folder"
            bluedeckFolder?.let { parents = listOf(it) }
        }
        return drive.files().create(metadata).setFields("id").execute().id
    }
}
