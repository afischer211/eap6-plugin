/*
   Copyright 2013 Red Hat, Inc. and/or its affiliates.

   This file is part of eap6 plugin.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package com.redhat.plugin.eap6;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.RepositorySystemSession;
import org.w3c.dom.Document;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;

/**
 * This abstract EAP6 Mojo initializes the module dictionaries and the skeleton file
 *
 * @author Yusuf Koer <ykoer@redhat.com>
 * @author Burak Serdar <bserdar@redhat.com>
 */
public abstract class AbstractEAP6Mojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession session;

    @Parameter(defaultValue = "true")
    protected Boolean generate = Boolean.TRUE;

    @Parameter(defaultValue = "false")
    protected Boolean verbose = Boolean.FALSE;

    @Parameter(defaultValue = "${basedir}/src/main/etc", required = true)
    protected File skeletonDir;

    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    protected String encoding;

    /**
     * Gives the location of dictionary files listing all available modules
     */
    @Parameter(property = "dictionaryFiles", required = false)
    protected List<File> dictionaryFiles;

    @Parameter(defaultValue = "${project.build.finalName}", required = true)
    protected String buildFinalName;

    /**
     * Gives the list of allowed dependency-scopes, default is &lt;provided&gt;
     * @since 1.0.1
     */
    @Parameter(property = "allowedDepScopes", required = true, defaultValue = Artifact.SCOPE_PROVIDED)
    protected List<String> allowedDepScopes;

    /**
     * Gives the list of allowed artifact-types
     * @since 1.0.1
     */
    @Parameter(property = "allowedDepTypes", required = false)
    protected List<String> allowedDepTypes;

    /**
     * Gives the list of excluded artifacts
     * @since 1.0.1
     */
    @Parameter(property = "excludedArtifacts", required = false)
    protected List<String> excludedArtifacts;

    protected Dictionaries dictionaries = new Dictionaries();
    protected Map<Artifact, String> artifactsAsModules;
    protected Map<String, Artifact> reverseMap = new HashMap<String, Artifact>();

    /**
     * Initialize mapping dictionaries
     *
     * @throws MojoFailureException
     */
    protected void initializeDictionaries() throws MojoFailureException {
        // Read the dictionary files
        try {
            // Load the default dictionary
            dictionaries.addDictionary(getClass().getResourceAsStream("/eap6.dict"));
            // load configured dictionaries
            for (File f : dictionaryFiles) {
                getLog().debug("Reading dict-file " + f.getName());
                dictionaries.addDictionary(f);
            }
        } catch (Exception e) {
            throw new MojoFailureException("Cannot load dictionaries", e);
        }

        // Get the artifacts
        @SuppressWarnings("unchecked")
        Set<Artifact> dependencies = project.getArtifacts();

        if (verbose) {
            for (Artifact x : dependencies)
                getLog().debug("Project-Dependency Artifact: <" + x + "> type: <" + x.getType() + "> scope: <" + x.getScope() + ">");
        }
        // Find artifacts that are not provided, but in the dictionary,
        // and warn
        Set<Artifact> artifactsNotMatchingScope = new TreeSet<Artifact>();
        Set<Artifact> artifactsNotMatchingType = new TreeSet<Artifact>();
        Set<Artifact> artifactsMatchingExPatterns = new TreeSet<Artifact>();

        // Find artifacts that should be in deployment structure, that is,
        // all artifacts that have a non-null mapping, and provided
        artifactsAsModules = new HashMap<Artifact, String>();

        reverseMap = new HashMap<String, Artifact>();

        getLog().info("Excluded artifacts: "+listToString(excludedArtifacts));

        ArtifactFilter artifactFilter = excludedArtifacts!=null?new StrictPatternExcludesArtifactFilter(excludedArtifacts):null;

        for (Artifact a : dependencies) {
            DictItem item = dictionaries.find(getLog(), a.getGroupId(), a.getArtifactId(), a.getVersion());
            if (item != null && item.getModuleName() != null) {
                reverseMap.put(item.getModuleName(), a);

                if (!isMatchingScope(a)) {
                    artifactsNotMatchingScope.add(a);
                } else {
                    if (!isMatchingType(a)) {
                        artifactsNotMatchingType.add(a);
                    } else {
                        if (artifactFilter!=null && !artifactFilter.include(a)) {
                            artifactsMatchingExPatterns.add(a);
                        } else {
                            artifactsAsModules.put(a, item.getModuleName());
                        }
                    }
                }
            } else {
                if (verbose) {
                    getLog().info("No matching dict-entry for artifact <" + a + "> found");
                }
            }
        }

        for (Artifact a : artifactsNotMatchingScope) {
            getLog().warn(
                    "EAP6: Artifact <" + a + "> is not of required scope \"" + listToString(allowedDepScopes) + "\", but can be included as an EAP6 module "
                            + dictionaries.find(getLog(), a.getGroupId(), a.getArtifactId(), a.getVersion()));
        }
        for (Artifact a : artifactsNotMatchingType) {
            getLog().warn("EAP6: Artifact <" + a + "> is not of required type \"" + listToString(allowedDepTypes) + "\"");
        }
        for (Artifact a : artifactsMatchingExPatterns) {
            getLog().warn("EAP6: Artifact <" + a + "> matches excluded artifact-patterns");
        }
    }

    protected String listToString(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String scope : list) {
            sb.append(scope + ",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    protected boolean isMatchingScope(Artifact a) {
        return allowedDepScopes.contains(a.getScope());
    }

    protected boolean isMatchingType(Artifact a) {
        return (allowedDepTypes.isEmpty() ? true : allowedDepTypes.contains(a.getType()));
    }

    protected Artifact findArtifact(String groupId, String artifactId) {
        @SuppressWarnings("unchecked")
        Set<Artifact> artifacts = project.getArtifacts();
        if (verbose) {
            getLog().debug("Searching " + groupId + ":" + artifactId + " in " + artifacts);
        }
        for (Artifact x : artifacts) {
            if (x.getGroupId().equals(groupId) && x.getArtifactId().equals(artifactId))
                return x;
        }
        return null;
    }

    protected Document initializeSkeletonFile(String skeletonFileName) throws MojoFailureException {
        // Is there a skeleton file?
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            File skeletonFile = null;
            if (skeletonDir != null)
                skeletonFile = new File(skeletonDir, skeletonFileName);
            if (!skeletonFile.exists())
                skeletonFile = null;
            if (skeletonFile != null) {
                doc = factory.newDocumentBuilder().parse(skeletonFile);
            } else {
                doc = factory.newDocumentBuilder().parse(getClass().getResourceAsStream("/" + skeletonFileName));
            }

            return doc;
        } catch (Exception e) {
            throw new MojoFailureException("Cannot initialize skeleton XML", e);
        }
    }

    protected void writeXmlFile(Document doc, File workDirectory, String fileName) throws MojoFailureException {
        File destinationFile = new File(workDirectory, fileName);
        try {
            FileOutputStream ostream = new FileOutputStream(destinationFile);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.transform(new DOMSource(doc), new StreamResult(ostream));
            ostream.close();
        } catch (Exception e) {
            throw new MojoFailureException("Cannot write output file", e);
        }
    }

    protected void writeXmlFile(String content, File workDirectory, String fileName) throws MojoFailureException {
        File destinationFile = new File(workDirectory, fileName);

        try {
            FileOutputStream ostream = new FileOutputStream(destinationFile);
            ostream.write(content.getBytes(Charset.forName(encoding)));
            ostream.close();
        } catch (Exception e) {
            throw new MojoFailureException("Cannot write output file", e);
        }
    }

    /**
     * method to convert Document to String
     *
     * @param doc
     * @return
     */
    protected String getStringFromDocument(Document doc) {
        try {
            DOMSource domSource = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.transform(domSource, result);
            return writer.toString();
        } catch (TransformerException ex) {
            getLog().error(ex);
            return null;
        }
    }

}
