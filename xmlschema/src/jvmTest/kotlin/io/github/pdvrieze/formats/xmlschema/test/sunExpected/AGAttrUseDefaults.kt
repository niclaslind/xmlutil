/*
 * Copyright (c) 2021.
 *
 * This file is part of xmlutil.
 *
 * This file is licenced to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You should have received a copy of the license with the source distribution.
 * Alternatively, you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.github.pdvrieze.formats.xmlschema.test.sunExpected

import io.github.pdvrieze.formats.xmlschema.XmlSchemaConstants
import io.github.pdvrieze.formats.xmlschema.datatypes.primitiveInstances.VNCName
import io.github.pdvrieze.formats.xmlschema.datatypes.primitiveInstances.VAnyURI
import io.github.pdvrieze.formats.xmlschema.datatypes.serialization.*
import nl.adaptivity.xmlutil.QName

object AGAttrUseDefaults {
    val ns = "AttrGroup/attrUse"

    val heightAttr = XSLocalAttribute(
        name = VNCName("height"),
        type = QName(XmlSchemaConstants.XS_NAMESPACE, "decimal", "xsd")
    )
    val ag = XSAttributeGroup(
        name = VNCName("aGr"),
        attributes = listOf(
            XSLocalAttribute(ref = QName(ns, "number", "tn")),
            heightAttr
        )
    )
    val expectedSchema = XSSchema(
        targetNamespace = VAnyURI(ns),
        elements = listOf(
            XSElement(name = VNCName("root")),
            XSElement(
                name = VNCName("elementWithAttr"),
                localType = XSLocalComplexTypeShorthand(
                    attributes = listOf(
                        XSLocalAttribute(
                            name = VNCName("good"),
                            type = QName(XmlSchemaConstants.XS_NAMESPACE, "string", "xsd")
                        )
                    ),
                    attributeGroups = listOf(
                        XSAttributeGroupRef(ref = QName(ns, "aGr", "tn"))
                    ),
                )
            )
        ),
        attributes = listOf(
            XSAttribute(
                name = VNCName("number"),
                type = QName(XmlSchemaConstants.XS_NAMESPACE, "integer", "xsd")
            )
        ),
        attributeGroups = listOf(ag)
    )

}
