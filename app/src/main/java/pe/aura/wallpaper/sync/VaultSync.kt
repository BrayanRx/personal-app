package pe.aura.wallpaper.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import pe.aura.wallpaper.data.ActivityRepository
import pe.aura.wallpaper.data.QuoteRepository
import pe.aura.wallpaper.data.Settings

/**
 * Lee la agenda y las frases desde la carpeta de Obsidian elegida por el usuario (SAF).
 * Pensado para llamarse en segundo plano desde la app, el fondo animado y el worker del bloqueo.
 */
object VaultSync {

    /** Desde el fondo animado se sincroniza como máximo cada 15 minutos. */
    const val WALLPAPER_MIN_INTERVAL_MS = 15 * 60 * 1000L

    private val lock = Any()

    /**
     * Sincroniza si hay carpeta configurada. Nunca lanza: los errores se guardan en [Settings].
     * @param force vuelve a leer las notas aunque no hayan cambiado ("Sincronizar ahora").
     * @param minIntervalMs no hace nada si el último intento fue hace menos de esto.
     * @return true si se guardaron datos nuevos.
     */
    fun sync(context: Context, force: Boolean = false, minIntervalMs: Long = 0): Boolean = synchronized(lock) {
        val settings = Settings(context)
        val tree = settings.syncTreeUri?.let(Uri::parse) ?: return false
        val now = System.currentTimeMillis()
        if (!force && minIntervalMs > 0 && now - settings.syncLastAttempt < minIntervalMs) return false
        settings.syncLastAttempt = now

        val files = try {
            listNotes(context, tree)
        } catch (e: SecurityException) {
            settings.saveSyncStatus(now, null, null, "Aura perdió el permiso para leer la carpeta. Vuelve a elegirla.")
            return false
        } catch (e: Exception) {
            settings.saveSyncStatus(now, null, null, "No se pudo leer la carpeta: ${e.message ?: e.javaClass.simpleName}")
            return false
        }

        val outcome = SyncCore.run(
            files, ActivityRepository(context), QuoteRepository(context),
            settings.syncAgendaSig, settings.syncQuotesSig, force,
        )
        settings.syncAgendaSig = outcome.agendaSig
        settings.syncQuotesSig = outcome.quotesSig
        settings.saveSyncStatus(now, outcome.activities, outcome.quotes, outcome.errors.joinToString("\n").ifEmpty { null })
        if (outcome.changed) settings.bumpDataVersion()
        outcome.changed
    }

    /** Lista los archivos de la carpeta (sin subcarpetas) con su firma de cambio. */
    private fun listNotes(context: Context, tree: Uri): List<NoteFile> {
        val cr = context.contentResolver
        if (cr.persistedUriPermissions.none { it.uri == tree && it.isReadPermission }) {
            throw SecurityException("permiso no persistido")
        }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE,
        )
        val out = mutableListOf<NoteFile>()
        cr.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(2) == Document.MIME_TYPE_DIR) continue
                val docId = c.getString(0)
                val name = c.getString(1) ?: continue
                val modified = if (c.isNull(3)) 0L else c.getLong(3)
                val size = if (c.isNull(4)) 0L else c.getLong(4)
                // Firma: fecha de modificación; si el proveedor no la da, el tamaño.
                val signature = if (modified > 0) "m:$modified" else "s:$size"
                val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                out += NoteFile(name, signature) {
                    cr.openInputStream(docUri)?.bufferedReader()?.use { it.readText() }
                        ?: error("No se pudo abrir $name")
                }
            }
        } ?: error("la carpeta no respondió")
        return out
    }

    /** "primary:Documents/obsidian/…/Aura" → "Documents/obsidian/…/Aura" */
    fun folderLabel(uri: String): String =
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfter(':') }.getOrDefault(uri)
}
