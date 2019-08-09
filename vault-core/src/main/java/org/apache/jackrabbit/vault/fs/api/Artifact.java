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

package org.apache.jackrabbit.vault.fs.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.jcr.RepositoryException;

/**
 * An artifact represents a fragment (or aspect) of a Vault file. This can either
 * be a:
 * <ul>
 * <li> primary (serialized) content
 * <li> additional serialized content
 * <li> binary attachments (eg. binary properties)
 * <li> directory representation
 * </ul>
 *
 * Artifacts are used for exporting (output) and importing (input). Output
 * artifacts are generated by the vault fs, input artifacts are used by the
 * respective import layer.
 * <p>
 * Depending on the type of the artifact and of it's source different access
 * methods for it's content are preferred:
 * If {@link AccessType#NONE} is set, no content is available. this is
 * the case for {@link ArtifactType#DIRECTORY} artifacts.<br>
 * If {@link AccessType#SPOOL} is set then clients should use the
 * {@link #spool(OutputStream)} method to retrieve the content. this is mostly
 * the case for output-artifacts that have serialized content.<br>
 * Tree.If {@link AccessType#STREAM} is set then clients should use the
 * {@link #getInputStream()} method in to retrieve the content. this is mostly
 * the case for input-artifacts or for {@link ArtifactType#BINARY} artifacts.
 * <p>
 * Each artifact contains a (repository) name and a possible extension. The name
 * and the extension must be preserved after a export/import roundtrip.
 */
public interface Artifact extends Dumpable {

    /**
     * Returns the relative path of this artifact in platform format including
     * the extension. eg: "_cq_nodeType.cnd" or "en/.content.xml" and eventual
     * intermediate extensions. eg: "image.png.dir/.content.xml"
     *
     * @return the relative platform path
     */
    public String getPlatformPath();

    /**
     * Returns the (repository) extension of this artifact. eg: ".jsp"
     * @return the (repository) extension of this artifact.
     */
    public String getExtension();

    /**
     * Returns the relative (repository) path of this artifact in respect to
     * it's parent node. eg: "myNodeType"
     *
     * @return the final name
     */
    public String getRelativePath();

    /**
     * Returns the type of this artifact.
     * @return the type of this artifact.
     */
    public ArtifactType getType();

    /**
     * Returns the serialization type of this artifact.
     * @return the serialization type of this artifact.
     */
    public SerializationType getSerializationType();

    /**
     * Returns the preferred access value for this artifact.
     * @return the preferred access value.
     */
    AccessType getPreferredAccess();

    /**
     * Returns the length of the serialized data if it's known without doing the
     * actual serialization.
     * @return the length or {@code -1} if the length cannot be determined.
     */
    long getContentLength();

    /**
     * Returns the content type of the serialized data or {@code null} if
     * the type is not known or cannot be determined.
     * @return the content type or {@code null}.
     */
    String getContentType();

    /**
     * Returns the last modified date or {@code 0} if not known.
     * @return the last modified date or {@code 0}
     */
    long getLastModified();

    /**
     * Writes the content to the given output stream and closes it afterwards.
     * This is the preferred method to use for output-artifacts.
     * <p>The specified stream remains open after this method returns.
     * @param out the output stream to spool to
     * @throws IOException if an I/O error occurs
     * @throws RepositoryException if a repository error occurs
     */
    void spool(OutputStream out) throws IOException, RepositoryException;

    /**
     * Returns the input stream to the contents of this artifact.
     * This is the preferred method to use for input-artifacts.
     *
     * @return a input stream to the contents of this artifact.
     * @throws IOException if an I/O error occurs
     * @throws RepositoryException if a repository error occurs
     */
    InputStream getInputStream() throws IOException, RepositoryException;

    /**
     * Returns an input source to the contents of this artifact.
     * This is also preferred for {@link AccessType#STREAM}.
     *
     * @return an input source.
     * @throws IOException if an I/O error occurs.
     * @throws RepositoryException of a repository error occurs.
     */
    VaultInputSource getInputSource() throws IOException, RepositoryException;


}