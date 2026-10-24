package rs.termin.app.core.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import rs.termin.app.core.common.IoDispatcher
import rs.termin.app.core.data.firebase.ImageUrls
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Pre uploada fotografija se smanjuje na najviše 1600 px (WebP, kvalitet ~80) i pravi se
 * umanjena verzija od 480 px za liste.
 */
@Singleton
class ImageUploader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storage: FirebaseStorage,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun upload(uri: Uri, path: String): String = withContext(io) {
        val bitmap = decode(uri)
        val metadata = storageMetadata {
            contentType = "image/webp"
            cacheControl = "public, max-age=31536000"
        }
        storage.reference.child(path).putBytes(compress(bitmap, MAX_SIZE, QUALITY), metadata).await()
        storage.reference.child(ImageUrls.thumbPath(path)).putBytes(compress(bitmap, THUMB_SIZE, THUMB_QUALITY), metadata).await()
        bitmap.recycle()
        path
    }

    suspend fun delete(path: String) = withContext(io) {
        runCatching { storage.reference.child(path).delete().await() }
        runCatching { storage.reference.child(ImageUrls.thumbPath(path)).delete().await() }
        Unit
    }

    private fun decode(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("Unsupported image")
        val rotation = context.contentResolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).rotationDegrees } ?: 0
        }
        if (rotation == 0) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        if (rotated != decoded) decoded.recycle()
        return rotated
    }

    private fun compress(source: Bitmap, maxSize: Int, quality: Int): ByteArray {
        val scale = minOf(1f, maxSize.toFloat() / max(source.width, source.height))
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else {
            source
        }
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        val out = ByteArrayOutputStream()
        bitmap.compress(format, quality, out)
        if (bitmap != source) bitmap.recycle()
        return out.toByteArray()
    }

    private companion object {
        const val MAX_SIZE = 1600
        const val THUMB_SIZE = 480
        const val QUALITY = 80
        const val THUMB_QUALITY = 75
    }
}
