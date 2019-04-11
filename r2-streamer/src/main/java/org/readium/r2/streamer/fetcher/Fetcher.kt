/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.fetcher

import org.readium.r2.shared.Publication
import org.readium.r2.streamer.container.Container
import java.io.InputStream

class Fetcher(var publication: Publication, var container: Container, private val userPropertiesPath: String?) {
    private var rootFileDirectory: String
    private var contentFilters: ContentFilters?

    init {
        val rootFilePath = publication.internalData["rootfile"]
                ?: throw Exception("Missing root file")
        if (rootFilePath.isNotEmpty() && rootFilePath.contains('/')) {
            rootFileDirectory = rootFilePath.replaceAfterLast("/", "", rootFilePath)
            rootFileDirectory = rootFileDirectory.dropLast(1)
        } else {
            rootFileDirectory = ""
        }
        contentFilters = getContentFilters(container.rootFile.mimetype)
    }

    fun data(path: String): ByteArray? {
        var data: ByteArray? = container.data(path)
        if (data != null)
            data = contentFilters?.apply(data, publication, container, path)
        return data
    }

    fun dataStream(path: String): InputStream {
        // get the page content (e.g. page-1-10.xhtml) as inputStream
        var inputStream = container.dataInputStream(path)

        // trying remove the default viewport to stop overriding the applied one.
        // try to use Regex to match viewport tag more accurate
        var pageContent = String(inputStream.readBytes())
        pageContent = pageContent.replace("<meta name=\"viewport\" content=\"initial-scale=2.3, user-scalable=no\" />", "")
        inputStream = pageContent.byteInputStream()

        // apply content on cureent page (e.g. meta, styles, javascript)
        inputStream = contentFilters?.apply(inputStream, publication, container, path) ?: inputStream
        return inputStream
    }

    fun dataLength(path: String): Long {
        val relativePath = rootFileDirectory.plus(path)

        publication.resource(path) ?: throw Exception("Missing file")
        return container.dataLength(relativePath)
    }

    private fun getContentFilters(mimeType: String?): ContentFilters {
        return when (mimeType) {
            "application/epub+zip", "application/oebps-package+xml" -> ContentFiltersEpub(userPropertiesPath)
            "application/vnd.comicbook+zip", "application/x-cbr" -> ContentFiltersCbz()
            else -> throw Exception("Missing container or MIMEtype")
        }
    }
}