package com.subeditor.android_subtitle_editor

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.util.MimeTypes
import fi.iki.elonen.NanoHTTPD
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import kotlin.random.Random

object CurrentServer {
    var designatedPort = 31218
    var server : HttpsServerForSMB? = null
    var tries = 3
}

class HttpsServerForSMB(port: Int, private val fileLocation: String, private val currentMimeType:String, private val ntlm: NtlmPasswordAuthentication) : NanoHTTPD(port) {
    private fun serveFile(header: Map<String, String>, file: SmbFile, mime: String?): Response {
        var res: Response
        try {
            val eTag =
                Integer.toHexString((file.canonicalPath + file.lastModified() + "" + file.length()).hashCode())

            // Support (simple) skipping:
            var startFrom: Long = 0
            var endAt: Long = -1
            var range = header["range"]
            if (range != null) {
                if (range.startsWith("bytes=")) {
                    range = range.substring("bytes=".length)
                    val minus = range.indexOf('-')
                    try {
                        if (minus > 0) {
                            startFrom = range.substring(0, minus).toLong()
                            endAt = range.substring(minus + 1).toLong()
                        }
                    } catch (ignored: NumberFormatException) {
                    }
                }
            }

            // get if-range header. If present, it must match eTag or else we
            // should ignore the range request
            val ifRange = header["if-range"]
            val headerIfRangeMissingOrMatching = (ifRange == null || (eTag == ifRange))
            val ifNoneMatch = header["if-none-match"]
            val headerIfNoneMatchPresentAndMatching =
                ifNoneMatch != null && (("*" == ifNoneMatch) || (ifNoneMatch == eTag))

            // Change return code and add Content-Range header when skipping is
            // requested
            val fileLen = file.length()
            if (headerIfRangeMissingOrMatching && (range != null) && (startFrom >= 0) && (startFrom < fileLen)) {
                // range request that matches current eTag
                // and the startFrom of the range is satisfiable
                if (headerIfNoneMatchPresentAndMatching) {
                    // range request that matches current eTag
                    // and the startFrom of the range is satisfiable
                    // would return range from file
                    // respond with not-modified
                    res = newFixedLengthResponse(Response.Status.NOT_MODIFIED, mime, "")
                    res.addHeader("ETag", eTag)
                } else {
                    if (endAt < 0) {
                        endAt = fileLen - 1
                    }
                    var newLen = endAt - startFrom + 1
                    if (newLen < 0) {
                        newLen = 0
                    }
                    val fis = file.inputStream
                    fis.skip(startFrom)
                    res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, fis, newLen)
                    res.addHeader("Accept-Ranges", "bytes")
                    res.addHeader("Content-Length", "" + newLen)
                    res.addHeader("Content-Range", "bytes $startFrom-$endAt/$fileLen")
                    res.addHeader("ETag", eTag)
                }
            } else {
                if (headerIfRangeMissingOrMatching && (range != null) && (startFrom >= fileLen)) {
                    // return the size of the file
                    // 4xx responses are not trumped by if-none-match
                    res = newFixedLengthResponse(
                        Response.Status.RANGE_NOT_SATISFIABLE,
                        MIME_PLAINTEXT,
                        ""
                    )
                    res.addHeader("Content-Range", "bytes */$fileLen")
                    res.addHeader("ETag", eTag)
                } else if (range == null && headerIfNoneMatchPresentAndMatching) {
                    // full-file-fetch request
                    // would return entire file
                    // respond with not-modified
                    res = newFixedLengthResponse(Response.Status.NOT_MODIFIED, mime, "")
                    res.addHeader("ETag", eTag)
                } else if (!headerIfRangeMissingOrMatching && headerIfNoneMatchPresentAndMatching) {
                    // range request that doesn't match current eTag
                    // would return entire (different) file
                    // respond with not-modified
                    res = newFixedLengthResponse(Response.Status.NOT_MODIFIED, mime, "")
                    res.addHeader("ETag", eTag)
                } else {
                    // supply the file
                    res = newFixedLengthResponse(Response.Status.OK, mime, file.inputStream, file.length())
                    res.addHeader("Content-Length", "" + fileLen)
                    res.addHeader("ETag", eTag)
                }
            }
        } catch (ioe: IOException) {
            res = newFixedLengthResponse("Reading file failed")
        }
        return res
    }
    override fun serve(session: IHTTPSession): Response {
        jcifs.Config.setProperty("jcifs.smb.client.dfs.disabled", "true")
        return try {
//            val ntlm  = KnownServers.getAuth(fileLocation, activity)
//            val smbFile123 = SmbFile(fileLocation, NtlmPasswordAuthentication.ANONYMOUS)
            val smbFileWithAuth = SmbFile(fileLocation, ntlm)
            serveFile(session.headers, smbFileWithAuth, currentMimeType)
        } catch (e: Exception) {
            val message = "Failed to load asset because $e"
            newFixedLengthResponse(message)
        }
    }
}

class CreateServer {
    private fun secureServerWithSSL(server: HttpsServerForSMB, context: Context): HttpsServerForSMB {
        val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
        val keyStoreStream: InputStream = context.assets.open("keystore1.bks")
        keystore.load(keyStoreStream, "myKeyStorePass".toCharArray())
        val keyManagerFactory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keystore, "myKeyStorePass".toCharArray())
        val sslServerSocketFactory =  NanoHTTPD.makeSSLSocketFactory(keystore, keyManagerFactory)
        server.makeSecure(sslServerSocketFactory, null)
        return server
    }
    private fun createServer(context: Context, location:String, currentMimeType: String, fragment: Fragment): HttpsServerForSMB {
        if (CurrentServer.server != null) {
            CurrentServer.server!!.stop()
        }
        val ntlm  = KnownServers.getAuth(location, fragment)
        val newServer = HttpsServerForSMB( CurrentServer.designatedPort, location, currentMimeType, ntlm)
        val server = secureServerWithSSL(newServer, context)
//        val server = SecurityWrapper().secureServerWithSSL(newServer, context)
//        newServer.makeSecure(sslServerSocketFactory, null)
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        CurrentServer.server = server
        return server
    }
    fun loadServer(path: String, fragment: Fragment) {
        val extension = MimeTypeMap.getFileExtensionFromUrl(path)
        if (extension != null) {
            var type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (type == null) {
                type = MimeTypes.VIDEO_MATROSKA
            }
            try {
                createServer(fragment.requireContext(), path, type, fragment)
            } catch (e: Exception) {
                if (CurrentServer.tries > 0) {
                    val newPort = Random.nextInt(30000, 48000)
                    CurrentServer.designatedPort = newPort
                    CurrentServer.tries -= 1
                    createServer(fragment.requireContext(), path, type, fragment)
                }
            }
        }
    }
}