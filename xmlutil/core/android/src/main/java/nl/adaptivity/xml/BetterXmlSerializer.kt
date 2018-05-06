/*
 * Copyright (c) 2018.
 *
 * This file is part of ProcessManager.
 *
 * ProcessManager is free software: you can redistribute it and/or modify it under the terms of version 3 of the
 * GNU Lesser General Public License as published by the Free Software Foundation.
 *
 * ProcessManager is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with ProcessManager.  If not,
 * see <http://www.gnu.org/licenses/>.
 */

package nl.adaptivity.xml

import org.xmlpull.v1.XmlSerializer

import javax.xml.XMLConstants

import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.Locale


class BetterXmlSerializer : XmlSerializer {

    private lateinit var writer: Writer

    private var pending: Boolean = false
    private var auto: Int = 0
    private var depth: Int = 0

    private var elementStack = arrayOfNulls<String>(12)

    private var nspCounts = IntArray(4)
    private var nspStack = arrayOfNulls<String>(10)
    private var nspWritten = BooleanArray(5)

    private var indent = BooleanArray(4)
    private var unicode: Boolean = false
    private var encoding: String? = null
    private val escapeAggressive = false
    var isOmitXmlDecl = false

    private fun check(close: Boolean) {
        if (!pending) {
            return
        }

        depth++
        pending = false

        if (indent.size <= depth) {
            val hlp = BooleanArray(depth + 4)
            System.arraycopy(indent, 0, hlp, 0, depth)
            indent = hlp
        }
        indent[depth] = indent[depth - 1]

        if (nspCounts.size <= depth + 1) {
            val hlp = IntArray(depth + 8)
            System.arraycopy(nspCounts, 0, hlp, 0, depth + 1)
            nspCounts = hlp
        }

        nspCounts[depth + 1] = nspCounts[depth]

        writer.write(if (close) " />" else ">")
    }

    @Throws(IOException::class)
    private fun writeEscaped(s: String, quot: Int) {

        loop@ for (i in 0 until s.length) {
            val c = s[i]
            when (c) {
                '&'              -> writer.write("&amp;")
                '>'              -> writer.write("&gt;")
                '<'              -> writer.write("&lt;")
                '"', '\''        -> {
                    if (c.toInt() == quot) {
                        writer.write(
                            if (c == '"') "&quot;" else "&apos;")
                        break@loop
                    }
                    if (escapeAggressive && quot != -1) {
                        writer.write("&#${c.toInt()};")
                    } else {
                        writer.write(c.toInt())
                    }
                }
                '\n', '\r', '\t' -> if (escapeAggressive && quot != -1) {
                    writer.write("&#${c.toInt()};")
                } else {
                    writer.write(c.toInt())
                }
                else             ->
                    //if(c < ' ')
                    //	throw new IllegalArgumentException("Illegal control code:"+((int) c));
                    if (escapeAggressive && (c < ' ' || c == '@' || c.toInt() > 127 && !unicode)) {
                        writer.write("&#${c.toInt()};")
                    } else {
                        writer.write(c.toInt())
                    }
            }
        }
    }

    override fun docdecl(dd: String) {
        writer.write("<!DOCTYPE")
        writer.write(dd)
        writer.write(">")
    }

    override fun endDocument() {
        while (depth > 0) {
            endTag(elementStack[depth * 3 - 3], elementStack[depth * 3 - 1]!!)
        }
        flush()
    }

    @Throws(IOException::class)
    override fun entityRef(name: String) {
        check(false)
        writer.write('&')
        writer.write(name)
        writer.write(';')
    }

    override fun getFeature(name: String): Boolean {
        //return false;
        return if ("http://xmlpull.org/v1/doc/features.html#indent-output" == name)
            indent[depth]
        else
            false
    }

    override fun getPrefix(namespace: String, create: Boolean): String? {
        return getPrefix(namespace, false, create)
    }

    private fun getPrefix(namespace: String, includeDefault: Boolean, create: Boolean): String? {

        run {
            var i = nspCounts[depth + 1] * 2 - 2
            while (i >= 0) {
                if (nspStack[i + 1] == namespace && (includeDefault || nspStack[i] != "")) {
                    var candidate: String? = nspStack[i]
                    for (j in i + 2 until nspCounts[depth + 1] * 2) {
                        if (nspStack[j] == candidate) {
                            candidate = null
                            break
                        }
                    }
                    if (candidate != null) {
                        return candidate
                    }
                }
                i -= 2
            }
        }

        if (!create) {
            return null
        }

        var prefix: String?

        if ("" == namespace) {
            prefix = ""
        } else {
            do {
                prefix = "n" + auto++
                var i = nspCounts[depth + 1] * 2 - 2
                while (i >= 0) {
                    if (prefix == nspStack[i]) {
                        prefix = null
                        break
                    }
                    i -= 2
                }
            } while (prefix == null)
        }

        val p = pending
        pending = false
        setPrefix(prefix, namespace)
        pending = p
        return prefix
    }

    override fun getProperty(name: String): Any {
        throw RuntimeException("Unsupported property")
    }

    @Throws(IOException::class)
    override fun ignorableWhitespace(s: String) {
        text(s)
    }

    override fun setFeature(name: String, value: Boolean) {
        if ("http://xmlpull.org/v1/doc/features.html#indent-output" == name) {
            indent[depth] = value
        } else {
            throw RuntimeException("Unsupported Feature")
        }
    }

    override fun setProperty(name: String, value: Any) {
        throw RuntimeException(
            "Unsupported Property:$value")
    }

    @Throws(IOException::class)
    override fun setPrefix(prefix: String?, namespace: String?) {

        val depth = this.depth + 1

        var i = nspCounts[depth] * 2 - 2
        while (i >= 0) {
            if (nspStack[i + 1] == (namespace ?: "") && nspStack[i] == (prefix ?: "")) {
                // bail out if already defined
                return
            }
            i -= 2
        }


        var pos = nspCounts[depth]++ shl 1

        addSpaceToNspStack()

        nspStack[pos++] = prefix ?: ""
        nspStack[pos] = namespace ?: ""
        nspWritten[nspCounts[depth] - 1] = false
    }

    private fun addSpaceToNspStack() {
        val nspCount = nspCounts[if (pending) depth + 1 else depth]
        val pos = nspCount shl 1
        if (nspStack.size < pos + 2) {
            run {
                val hlp = arrayOfNulls<String>(nspStack.size + 16)
                System.arraycopy(nspStack, 0, hlp, 0, pos)
                nspStack = hlp
            }
            run {
                val help = BooleanArray(nspWritten.size + 8)
                System.arraycopy(nspWritten, 0, help, 0, nspCount)
                nspWritten = help
            }
        }
    }

    override fun setOutput(writer: Writer) {
        this.writer = writer

        nspCounts[0] = 3
        nspCounts[1] = 3
        nspStack[0] = ""
        nspStack[1] = ""
        nspStack[2] = "xml"
        nspStack[3] = "http://www.w3.org/XML/1998/namespace"
        nspStack[4] = "xmlns"
        nspStack[5] = "http://www.w3.org/2000/xmlns/"
        pending = false
        auto = 0
        depth = 0

        unicode = false
    }

    @Throws(IOException::class)
    override fun setOutput(os: OutputStream?, encoding: String?) {
        if (os == null) {
            throw IllegalArgumentException()
        }
        val streamWriter = when (encoding) {
            null -> OutputStreamWriter(os)
            else -> OutputStreamWriter(os, encoding)
        }
        setOutput(streamWriter)

        this.encoding = encoding
        if (encoding?.toLowerCase(Locale.ENGLISH)?.startsWith("utf") == true) {
            unicode = true
        }
    }

    override fun startDocument(encoding: String?, standalone: Boolean?) {
        if (!isOmitXmlDecl) {
            writer.write("<?xml version='1.0' ")

            if (encoding != null) {
                this.encoding = encoding
                if (encoding.toLowerCase(Locale.ENGLISH).startsWith("utf")) {
                    unicode = true
                }
            }

            if (this.encoding != null) {
                writer.write("encoding='")
                writer.write(this.encoding!!)
                writer.write("' ")
            }

            if (standalone != null) {
                writer.write("standalone='")
                writer.write(if (standalone) "yes" else "no")
                writer.write("' ")
            }
            writer.write("?>")
        }
    }

    @Throws(IOException::class)
    override fun startTag(namespace: String?, name: String): BetterXmlSerializer {
        check(false)

        //        if (namespace == null)
        //            namespace = "";

        if (indent[depth]) {
            writer.write("\r\n")
            for (i in 0 until depth) {
                writer.write("  ")
            }
        }

        var esp = depth * 3

        if (elementStack.size < esp + 3) {
            val hlp = arrayOfNulls<String>(elementStack.size + 12)
            System.arraycopy(elementStack, 0, hlp, 0, esp)
            elementStack = hlp
        }

        val prefix = namespace?.let { getPrefix(namespace, true, true) } ?: ""

        if (namespace.isNullOrEmpty()) {
            for (i in nspCounts[depth] until nspCounts[depth + 1]) {
                if (nspStack[i * 2] == "" && nspStack[i * 2 + 1] != "") {
                    throw IllegalStateException("Cannot set default namespace for elements in no namespace")
                }
            }
        }

        elementStack[esp++] = namespace
        elementStack[esp++] = prefix
        elementStack[esp] = name

        writer.write('<')
        if (prefix.isNotEmpty()) {
            writer.write(prefix)
            writer.write(':')
        }

        writer.write(name)

        pending = true

        return this
    }

    @Throws(IOException::class)
    override fun attribute(namespace: String?,
                           name: String,
                           value: String): BetterXmlSerializer {
        if (!pending) {
            throw IllegalStateException("illegal position for attribute")
        }

        val ns = namespace ?: ""

        if (ns == XMLConstants.XMLNS_ATTRIBUTE_NS_URI) {
            return namespace(name, value) // If it is a namespace attribute, just go there.
        } else if (ns == XMLConstants.NULL_NS_URI && XMLConstants.XMLNS_ATTRIBUTE == name) {
            return namespace("", value) // If it is a namespace attribute, just go there.
        }

        //		depth--;
        //		pending = false;

        val prefix = when (ns) {
            ""   -> ""
            else -> getPrefix(ns, false, true)
        }

        writer.write(' ')
        if ("" != prefix) {
            writer.write(prefix!!)
            writer.write(':')
        }
        writer.write(name)
        writer.write('=')
        val q = if (value.indexOf('"') == -1) '"' else '\''
        writer.write(q.toInt())
        writeEscaped(value, q.toInt())
        writer.write(q.toInt())

        return this
    }

    @Throws(IOException::class)
    fun namespace(
        prefix: String,
        namespace: String?): BetterXmlSerializer {

        if (!pending) {
            throw IllegalStateException("illegal position for attribute")
        }

        var wasSet = false
        for (i in nspCounts[depth] until nspCounts[depth + 1]) {
            if (prefix == nspStack[i * 2]) {
                if (nspStack[i * 2 + 1] != namespace) { // If we find the prefix redefined within the element, bail out
                    throw IllegalArgumentException(
                        "Attempting to bind prefix to conflicting values in one element")
                }
                if (nspWritten[i]) {
                    // otherwise just ignore the request.
                    return this
                }
                nspWritten[i] = true
                wasSet = true
                break
            }
        }

        if (!wasSet) { // Don't use setPrefix as we know it isn't there
            addSpaceToNspStack()
            val pos = nspCounts[depth + 1]++ shl 1
            nspStack[pos] = prefix
            nspStack[pos + 1] = namespace
            nspWritten[pos shr 1] = true
        }

        val nsNotNull = namespace ?: ""

        writer.write(' ')
        writer.write(XMLConstants.XMLNS_ATTRIBUTE)
        if (prefix.isNotEmpty()) {
            writer.write(':')
            writer.write(prefix)
        }
        writer.write('=')
        val q = if (nsNotNull.indexOf('"') == -1) '"' else '\''
        writer.write(q.toInt())
        writeEscaped(nsNotNull, q.toInt())
        writer.write(q.toInt())

        return this
    }

    @Throws(IOException::class)
    override fun flush() {
        check(false)
        writer.flush()
    }

    @Throws(IOException::class)
    override fun endTag(namespace: String?, name: String): BetterXmlSerializer {

        if (!pending) {
            depth--
        }
        //        if (namespace == null)
        //          namespace = "";

        if (namespace == null && elementStack[depth * 3] != null
            || namespace != null && namespace != elementStack[depth * 3]
            || elementStack[depth * 3 + 2] != name) {
            throw IllegalArgumentException("</{$namespace}$name> does not match start")
        }

        if (pending) {
            check(true)
            depth--
        } else {
            if (indent[depth + 1]) {
                writer.write("\r\n")
                for (i in 0 until depth) {
                    writer.write("  ")
                }
            }

            writer.write("</")
            val prefix = elementStack[depth * 3 + 1]
            if ("" != prefix) {
                writer.write(prefix)
                writer.write(':')
            }
            writer.write(name)
            writer.write('>')
        }

        nspCounts[depth + 1] = nspCounts[depth]
        return this
    }

    override fun getNamespace(): String? {
        return if (getDepth() == 0) null else elementStack[getDepth() * 3 - 3]
    }

    override fun getName(): String? {
        return if (getDepth() == 0) null else elementStack[getDepth() * 3 - 1]
    }

    override fun getDepth(): Int {
        return if (pending) depth + 1 else depth
    }

    @Throws(IOException::class)
    override fun text(text: String): BetterXmlSerializer {
        check(false)
        indent[depth] = false
        writeEscaped(text, -1)
        return this
    }

    @Throws(IOException::class)
    override fun text(text: CharArray, start: Int, len: Int): BetterXmlSerializer {
        text(String(text, start, len))
        return this
    }

    @Throws(IOException::class)
    override fun cdsect(data: String) {
        check(false)
        writer.write("<![CDATA[")
        writer.write(data)
        writer.write("]]>")
    }

    @Throws(IOException::class)
    override fun comment(comment: String) {
        check(false)
        writer.write("<!--")
        writer.write(comment)
        writer.write("-->")
    }

    @Throws(IOException::class)
    override fun processingInstruction(pi: String) {
        check(false)
        writer.write("<?")
        writer.write(pi)
        writer.write("?>")
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Writer.write(c: Char) = write(c.toInt())