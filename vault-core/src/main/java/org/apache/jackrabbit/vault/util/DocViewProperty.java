/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jackrabbit.vault.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.jcr.Binary;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.api.ReferenceBinary;
import org.apache.jackrabbit.commons.jackrabbit.SimpleReferenceBinary;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.util.XMLChar;
import org.apache.jackrabbit.value.ValueHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Helper class that represents a JCR property in the FileVault (enhanced) document view format.
 * It contains formatting and parsing methods for writing/reading enhanced
 * docview properties.
 * <br>
 * The string representation adheres to the following grammar:
 * <pre>
 * <code>prop:= [ "{" type "}" ] ( value | "[" [ value { "," value } ] "]" )
 * type := {@link PropertyType#nameFromValue(int)} | {@link #BINARY_REF}
 * value := is a string representation of the value where the following characters are escaped: ',\[{' with a leading '\'
 * </code>
 * </pre>
 * @deprecated Use {@link DocViewProperty2} instead.
 */
@Deprecated
public class DocViewProperty {

    public static final String BINARY_REF = "BinaryRef";

    /**
     * name of the property
     */
    public final String name;

    /**
     * value(s) of the property. always contains at least one value if this is
     * not a mv property.
     */
    public final String[] values;

    /**
     * indicates a multi-value property
     */
    public final boolean isMulti;

    /**
     * type of this property (can be undefined)
     */
    public final int type;

    /**
     * indicates a binary ref property
     */
    public final boolean isReferenceProperty;

    /**
     * set of unambigous property names (which never need an explicit type descriptor as the types are defined by the spec)
     */
    private static final Set<String> UNAMBIGOUS = new HashSet<>();
    static {
        UNAMBIGOUS.add("jcr:primaryType");
        UNAMBIGOUS.add("jcr:mixinTypes");
    }

    /**
     * Creates a new property based on an array of {@link Value}s
     * @param name the name of the property
     * @param values the values (always an array, may be empty), must not contain {@code null} items
     * @param type the type of the property
     * @param isMulti {@code true} in case this is a multivalue property
     * @param sort {@code true} in case the value array should be sorted first
     * @param useBinaryReferences to use the binary reference as value (if available)
     * @return the new property
     * @throws RepositoryException
     */
    public static DocViewProperty fromValues(@NotNull String name, @NotNull Value[] values, int type, boolean isMulti, boolean sort, boolean useBinaryReferences) throws RepositoryException {
        List<String> strValues = new ArrayList<>();
        if (isMulti) {
            if (sort) {
                Arrays.sort(values, ValueComparator.getInstance());
            }
        }
        for (Value value : values) {
            strValues.add(serializeValue(value, useBinaryReferences));
        }
        
        Boolean isBinaryRef = null;
        if (type == PropertyType.BINARY) {
            // either only binary references or regular binaries
            for (String strValue : strValues) {
                boolean isCurrentValueBinaryRef = !strValue.isEmpty();
                if (isBinaryRef == null) {
                    isBinaryRef = isCurrentValueBinaryRef;
                } else {
                    if (isBinaryRef != isCurrentValueBinaryRef) {
                        throw new ValueFormatException("Mixed binary references and regular binary values in the same multi-value property is not supported");
                    }
                }
            }
        }
        if (isBinaryRef == null) {
            isBinaryRef = false;
        }
        return new DocViewProperty(name, strValues.toArray(new String[0]), isMulti, type, isBinaryRef);
    }

    /**
     * Creates a new property based on a JCR {@link Property} object
     * @param prop the JCR property
     * @param sort if {@code true} multi-value properties should be sorted
     * @param useBinaryReferences {@code true} to use binary references
     * @return the new property
     * @throws IllegalArgumentException if single value property and not exactly 1 value is given.
     * @throws RepositoryException if another error occurs
     */
    public static DocViewProperty fromProperty(@NotNull Property prop, boolean sort, boolean useBinaryReferences) throws RepositoryException {
        boolean isMultiValue = prop.getDefinition().isMultiple();
        final Value[] values;
        if (isMultiValue) {
            values = prop.getValues();
        } else {
            values = new Value[] { prop.getValue() };
        }
        return fromValues(prop.getName(), values, prop.getType(), isMultiValue, sort, useBinaryReferences);
    }

    public static DocViewProperty fromDocViewProperty2(DocViewProperty2 property) {
        return new DocViewProperty(property.getName().toString(), property.getStringValues().toArray(new String[0]), property.isMultiValue(), property.getType(), property.isReferenceProperty());
    }

    static String serializeValue(Value value, boolean useBinaryReferences) throws RepositoryException {
        // special handling for binaries
        String strValue = null;
        if (value.getType() == PropertyType.BINARY) {
            if (useBinaryReferences) {
                Binary bin = value.getBinary();
                if (bin instanceof ReferenceBinary) {
                    strValue = ((ReferenceBinary) bin).getReference();
                }
            }
            if (strValue == null) {
                // leave value empty for non reference binaries or where reference is null
                strValue = "";
            }
        } else {
            strValue = ValueHelper.serialize(value, false);
        }
        return strValue;
    }

    /**
     * Creates a new property.
     * @param name name of the property
     * @param values values.
     * @param multi multiple flag
     * @param type type of the property
     * @throws IllegalArgumentException if single value property and not exactly 1 value is given.
     */
    public DocViewProperty(String name, String[] values, boolean multi, int type) {
        this(name, values, multi, type, false);
    }

    /**
     * Creates a new property.
     * @param name name of the property
     * @param values string representation of values.
     * @param multi indicates if this is a multi-value property
     * @param type type of the property
     * @param isRef {@code true} to indicate that this is a binary reference property
     * @throws IllegalArgumentException if single value property and not exactly 1 value is given.
     */
    public DocViewProperty(String name, String[] values, boolean multi, int type, boolean isRef) {
        this.name = name;
        this.values = values;
        isMulti = multi;
        // validate type
        if (type == PropertyType.UNDEFINED) {
            if ("jcr:primaryType".equals(name) || "jcr:mixinTypes".equals(name)) {
                type = PropertyType.NAME;
            }
        }
        this.type = type;
        if (!isMulti && values.length != 1) {
            throw new IllegalArgumentException("Single value property needs exactly 1 value.");
        }
        this.isReferenceProperty = isRef;
    }

    /**
     * Parses a enhanced docview property string and returns the property.
     * @param name name of the property
     * @param value (attribute) value
     * @return a property
     */
    public static DocViewProperty parse(String name, String value) {
        boolean isMulti = false;
        boolean isBinaryRef = false;
        int type = PropertyType.UNDEFINED;
        int pos = 0;
        char state = 'b';
        List<String> vals = null;
        StringBuilder tmp = new StringBuilder();
        int unicode = 0;
        int unicodePos = 0;
        while (pos < value.length()) {
            char c = value.charAt(pos++);
            switch (state) {
                case 'b': // begin (type or array or value)
                    if (c == '{') {
                        state = 't';
                    } else if (c == '[') {
                        isMulti = true;
                        state = 'v';
                    } else if (c == '\\') {
                        state = 'e';
                    } else {
                        tmp.append(c);
                        state = 'v';
                    }
                    break;
                case 'a': // array (array or value)
                    if (c == '[') {
                        isMulti = true;
                        state = 'v';
                    } else if (c == '\\') {
                        state = 'e';
                    } else {
                        tmp.append(c);
                        state = 'v';
                    }
                    break;
                case 't':
                    if (c == '}') {
                        if (BINARY_REF.equals(tmp.toString())) {
                            type = PropertyType.BINARY;
                            isBinaryRef = true;
                        } else {
                            type = PropertyType.valueFromName(tmp.toString());
                        }
                        tmp.setLength(0);
                        state = 'a';
                    } else {
                        tmp.append(c);
                    }
                    break;
                case 'v': // value
                    if (c == '\\') {
                        state = 'e';
                    } else if (c == ',' && isMulti) {
                        if (vals == null) {
                            vals = new LinkedList<String>();
                        }
                        vals.add(tmp.toString());
                        tmp.setLength(0);
                    } else if (c == ']' && isMulti && pos == value.length()) {
                        if (tmp.length() > 0 || vals != null) {
                            if (vals == null) {
                                vals = new LinkedList<String>();
                            }
                            vals.add(tmp.toString());
                            tmp.setLength(0);
                        }
                    } else {
                        tmp.append(c);
                    }
                    break;
                case 'e': // escaped
                    if (c == 'u') {
                        state = 'u';
                        unicode = 0;
                        unicodePos = 0;
                    } else if (c == '0') {
                        // special case to treat empty values. see JCR-3661
                        state = 'v';
                        if (vals == null) {
                            vals = new LinkedList<String>();
                        }
                    } else {
                        state = 'v';
                        tmp.append(c);
                    }
                    break;
                case 'u': // unicode escaped
                    unicode = (unicode << 4) + Character.digit(c, 16);
                    if (++unicodePos == 4) {
                        tmp.appendCodePoint(unicode);
                        state = 'v';
                    }
                    break;

            }
        }
        String[] values;
        if (isMulti) {
            // add value if missing ']'
            if (tmp.length() > 0) {
                if (vals == null) {
                    vals = new LinkedList<String>();
                }
                vals.add(tmp.toString());
            }
            if (vals == null) {
                values = Constants.EMPTY_STRING_ARRAY;
            } else {
                values = vals.toArray(new String[vals.size()]);
            }
        } else {
            values = new String[]{tmp.toString()};
        }
        return new DocViewProperty(name, values, isMulti, type, isBinaryRef);
    }
    /**
     * Formats (serializes) the given JCR property value according to the enhanced docview syntax.
     * @param prop the JCR property
     * @return the formatted string of the property value
     * @throws RepositoryException if a repository error occurs
     */
    public static String format(Property prop) throws RepositoryException {
        return format(prop, false, false);
    }
    
    /**
     * Formats (serializes) the given JCR property value to the enhanced docview syntax.
     * @param prop the JCR property
     * @param sort if {@code true} multi-value properties are sorted
     * @param useBinaryReferences {@code true} to use binary references
     * @return the formatted string of the property value
     * @throws RepositoryException if a repository error occurs
     */
    public static String format(Property prop, boolean sort, boolean useBinaryReferences)
            throws RepositoryException {
        return fromProperty(prop, sort, useBinaryReferences).formatValue();
    }

    /** 
     * Generates string representation of this DocView property value.
     * @return the string representation of the value
     */
    public String formatValue() {
        StringBuilder attrValue = new StringBuilder();
        
        if (isAmbiguous(type, name)) {
            final String strType;
            if (isReferenceProperty) {
                strType = BINARY_REF;
            } else {
                strType = PropertyType.nameFromValue(type);
            }
            attrValue.append('{').append(strType).append('}');
        }
        if (isMulti) {
            attrValue.append('[');
        }
        for (int i=0;i<values.length;i++) {
            String value = values[i];
            if (values.length == 1 && value.length() == 0) {
                // special case for empty string MV value (JCR-3661)
                attrValue.append("\\0");
            } else {
                if (i > 0) {
                    attrValue.append(',');
                }
                switch (type) {
                    case PropertyType.STRING:
                    case PropertyType.NAME:
                    case PropertyType.PATH:
                        attrValue.append(escape(value, isMulti));
                        break;
                    default:
                        attrValue.append(value);
                }
            }
        }
        if (isMulti) {
            attrValue.append(']');
        }
        return attrValue.toString();
    }

    /**
     * Escapes the value
     * @param buf buffer to append to
     * @param value value to escape
     * @param isMulti indicates multi value property
     * @deprecated Rather use {@link #escape(String, boolean)}
     */
    @Deprecated
    protected static void escape(StringBuffer buf, String value, boolean isMulti) {
        buf.append(escape(value, isMulti));
    }

    /**
     * Escapes the value
     * @param value value to escape
     * @param isMulti indicates multi value property
     * @return the escaped value
     */
    protected static String escape(String value, boolean isMulti) {
        StringBuilder buf = new StringBuilder();
        for (int i=0; i<value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                buf.append("\\\\");
            } else if (c == ',' && isMulti) {
                buf.append("\\,");
            } else if (i == 0 && !isMulti && (c == '[' || c == '{')) {
                buf.append('\\').append(c);
            } else if ( XMLChar.isInvalid(c)) {
                buf.append("\\u");
                buf.append(Text.hexTable[(c >> 12) & 15]);
                buf.append(Text.hexTable[(c >> 8) & 15]);
                buf.append(Text.hexTable[(c >> 4) & 15]);
                buf.append(Text.hexTable[c & 15]);
            } else {
                buf.append(c);
            }
        }
        return buf.toString();
    }
    
    /**
     * Checks if the type of the given property is ambiguous in respect to it's
     * property definition. the current implementation just checks some well
     * known properties.
     *
     * @param prop the property
     * @return type
     * @throws RepositoryException if a repository error occurs
     * @deprecated was not supposed to be public but rather is an implementation detail, should not be called at all
     */
    @Deprecated
    public static boolean isAmbiguous(Property prop) throws RepositoryException {
        return isAmbiguous(prop.getType(), prop.getName());
    }

    /**
     * Checks if the type of the given property is ambiguous in respect to it's
     * property definition. the current implementation just checks some well
     * known properties.
     *
     * @param type the type
     * @param name the name
     * @return {@code true} if type information should be emitted, otherwise {@code false}
     */
    private static boolean isAmbiguous(int type, String name) {
        return type != PropertyType.STRING && !UNAMBIGOUS.contains(name);
    }

    /**
     * Sets this property on the given node
     *
     * @param node the node
     * @return {@code true} if the value was modified.
     * @throws RepositoryException if a repository error occurs
     */
    public boolean apply(Node node) throws RepositoryException {
        Property prop = node.hasProperty(name) ? node.getProperty(name) : null;
        // check if multiple flags are equal
        if (prop != null && isMulti != prop.getDefinition().isMultiple()) {
            prop.remove();
            prop = null;
        }
        if (prop != null) {
            int propType = prop.getType();
            if (propType != type && (propType != PropertyType.STRING || type != PropertyType.UNDEFINED)) {
                // never compare if types differ
                prop = null;
            }
        }
        if (isMulti) {
            Value[] vs = prop == null ? null : prop.getValues();
            if (type == PropertyType.BINARY) {
                return applyBinary(node, vs);
            }
            if (vs != null && vs.length == values.length) {
                // quick check all values
                boolean modified = false;
                for (int i=0; i<vs.length; i++) {
                    if (!vs[i].getString().equals(values[i])) {
                        modified = true;
                    }
                }
                if (!modified) {
                    return false;
                }
            }
            if (type == PropertyType.UNDEFINED) {
                node.setProperty(name, values);
            } else {
                node.setProperty(name, values, type);
            }
            // assume modified
            return true;
        } else {
            Value v = prop == null ? null : prop.getValue();
            if (type == PropertyType.BINARY) {
                return applyBinary(node, v);
            }
            if (v == null || !v.getString().equals(values[0])) {
                try {
                    if (type == PropertyType.UNDEFINED) {
                        node.setProperty(name, values[0]);
                    } else {
                        node.setProperty(name, values[0], type);
                    }
                } catch (ValueFormatException e) {
                    // forcing string
                    node.setProperty(name, values[0], PropertyType.STRING);
                }
                return true;
            }
        }
        return false;
    }

    private boolean applyBinary(Node node, Value... existingValues) throws ValueFormatException, VersionException, LockException, ConstraintViolationException, RepositoryException {
        List<Value> binaryValues = new ArrayList<>(values.length);
        if (!isReferenceProperty) {
            for (String value : values) {
                // empty string is used for binary properties which should not be touched!
                if (!value.isEmpty()) { 
                    throw new InvalidSerializedDataException("Inline binaries are only supported as binary references, but is " + value);
                }
            }
            // just silently ignore binaries with only empty string values
            return false;
        }
        try {
            boolean modified = false;
            for (int n=0; n < values.length; n++) {
                String value = values[n];
                ReferenceBinary ref = new SimpleReferenceBinary(value);
                Value binaryValue = node.getSession().getValueFactory().createValue(ref);
                binaryValues.add(binaryValue);
                // compare with existing value
                if (modified == false && existingValues != null && n < existingValues.length && existingValues[n] != null) {
                    Binary existingBinary = existingValues[0].getBinary();
                    if (!existingBinary.equals(binaryValue.getBinary())) {
                        modified = true;
                    }
                } else {
                    modified = true;
                }
            }
            if (!modified) {
                return false;
            }
            if (isMulti) {
                node.setProperty(name, binaryValues.toArray(new Value[0]));
            } else {
                node.setProperty(name, binaryValues.get(0));
            }
            // the binary property is always modified (TODO: check if still correct with JCRVLT-110)
            return true;
        } finally {
            for (Value value : binaryValues) {
                value.getBinary().dispose();
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (isMulti ? 1231 : 1237);
        result = prime * result + (isReferenceProperty ? 1231 : 1237);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + type;
        result = prime * result + Arrays.hashCode(values);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DocViewProperty other = (DocViewProperty) obj;
        if (isMulti != other.isMulti)
            return false;
        if (isReferenceProperty != other.isReferenceProperty)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (type != other.type)
            return false;
        if (!Arrays.equals(values, other.values))
            return false;
        return true;
    }

    /**
     * This does not return the string representation of the enhanced docview property value but rather a descriptive string including the property name for debugging purposes.
     * Use {@link #formatValue()}, {@link #format(Property)} or {@link #format(Property, boolean, boolean)} to get the enhanced docview string representation of the value.
     */
    @Override
    public String toString() {
        return "DocViewProperty [name=" + name + ", values=" + Arrays.toString(values) + ", isMulti=" + isMulti + ", type=" + PropertyType.nameFromValue(type)
                + ", isReferenceProperty=" + isReferenceProperty + "]";
    }

}