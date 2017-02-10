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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSException;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * This abstract EAP6 Mojo initializes the module dictionaries and the skeleton file
 *
 * @author Yusuf Koer <ykoer@redhat.com>
 * @author Burak Serdar <bserdar@redhat.com>
 */
public abstract class AbstractEAP6Mojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "true")
    protected Boolean generate = Boolean.TRUE;

    @Parameter(defaultValue = "false")
    protected Boolean verbose = Boolean.FALSE;

    @Parameter(defaultValue = "${basedir}/src/main/etc", required = true)
    protected File skeletonDir;

    /**
     * Folder for generated artifacts; if null, then it will be calculated based on project packaging-type
     *
     * @since 1.0.1
     */
    @Parameter
    protected File destinationDir;

    /**
     * Print warnings about dependencies with false scope/type or excluded artifacts
     *
     * @since 1.0.1
     */
    @Parameter(defaultValue = "false")
    protected Boolean printArtifactWarnings = Boolean.FALSE;

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
     *
     * @since 1.0.1
     */
    @Parameter(property = "allowedDepScopes", required = true, defaultValue = Artifact.SCOPE_PROVIDED)
    protected List<String> allowedDepScopes;

    /**
     * Gives the list of allowed artifact-types
     *
     * @since 1.0.1
     */
    @Parameter(property = "allowedDepTypes", required = false)
    protected List<String> allowedDepTypes;

    /**
     * Gives the list of excluded artifacts
     *
     * @since 1.0.1
     */
    @Parameter(property = "excludedArtifacts", required = false)
    protected List<String> excludedArtifacts;

    /**
     * Activates the adding of destinationDir to project resources
     *
     * @since 1.0.1
     */
    @Parameter(defaultValue = "false")
    protected Boolean addResourceFolder = Boolean.FALSE;

    // Injection of BuildContext for m2e-compatibility
    @Component
    protected BuildContext buildContext;

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
            for (final File f : dictionaryFiles) {
                if (f != null && f.canRead()) {
                    getLog().debug("Reading dict-file " + f.getName());
                    dictionaries.addDictionary(f);
                }
            }
        } catch (final Exception e) {
            throw new MojoFailureException("Cannot load dictionaries", e);
        }

        // Get the artifacts
        Set<Artifact> dependencies;
        if (project != null) {
            dependencies = project.getArtifacts();
        } else {
            dependencies = new HashSet<Artifact>();
            getLog().warn("No project available, getting dependencies skipped");
        }

        if (verbose) {
            for (final Artifact x : dependencies)
                getLog().debug("Project-Dependency Artifact: <" + x + "> type: <" + x.getType() + "> scope: <" + x.getScope() + ">");
        }
        // Find artifacts that are not provided, but in the dictionary,
        // and warn
        final Set<Artifact> artifactsNotMatchingScope = new TreeSet<Artifact>();
        final Set<Artifact> artifactsNotMatchingType = new TreeSet<Artifact>();
        final Set<Artifact> artifactsMatchingExPatterns = new TreeSet<Artifact>();

        // Find artifacts that should be in deployment structure, that is,
        // all artifacts that have a non-null mapping, and provided
        artifactsAsModules = new HashMap<Artifact, String>();

        reverseMap = new HashMap<String, Artifact>();

        getLog().info("Excluded artifacts: " + listToString(excludedArtifacts));

        final ArtifactFilter artifactFilter = excludedArtifacts != null ? new StrictPatternExcludesArtifactFilter(excludedArtifacts) : null;

        for (final Artifact a : dependencies) {
            final DictItem item = dictionaries.find(getLog(), a.getGroupId(), a.getArtifactId(), a.getVersion());
            if (item != null && item.getModuleName() != null) {
                reverseMap.put(item.getModuleName(), a);

                if (!isMatchingScope(a)) {
                    artifactsNotMatchingScope.add(a);
                } else {
                    if (!isMatchingType(a)) {
                        artifactsNotMatchingType.add(a);
                    } else {
                        if (artifactFilter != null && !artifactFilter.include(a)) {
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

        for (final Artifact a : artifactsNotMatchingScope) {
            if (printArtifactWarnings) {
                getLog().warn("EAP6: Artifact <" + a + "> is not of required scope \"" + listToString(allowedDepScopes)
                        + "\", but can be included as an EAP6 module " + dictionaries.find(getLog(), a.getGroupId(), a.getArtifactId(), a.getVersion()));
            }
        }
        for (final Artifact a : artifactsNotMatchingType) {
            if (printArtifactWarnings) {
                getLog().warn("EAP6: Artifact <" + a + "> is not of required type \"" + listToString(allowedDepTypes) + "\"");
            }
        }
        for (final Artifact a : artifactsMatchingExPatterns) {
            if (printArtifactWarnings) {
                getLog().warn("EAP6: Artifact <" + a + "> matches excluded artifact-patterns");
            }
        }
    }

    protected String listToString(final List<String> list) {
        final StringBuilder sb = new StringBuilder();
        if (list == null || list.size()<=0) {
            return sb.toString();
        }
        
        for (final String scope : list) {
            sb.append(scope).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    protected boolean isMatchingScope(final Artifact a) {
        return allowedDepScopes.contains(a.getScope());
    }

    protected boolean isMatchingType(final Artifact a) {
        return (allowedDepTypes.isEmpty() ? true : allowedDepTypes.contains(a.getType()));
    }

    protected Artifact findArtifact(final String groupId, final String artifactId) {
        final Set<Artifact> artifacts = project.getArtifacts();
        if (verbose) {
            getLog().debug("Searching " + groupId + ":" + artifactId + " in " + artifacts);
        }
        for (final Artifact x : artifacts) {
            if (x.getGroupId().equals(groupId) && x.getArtifactId().equals(artifactId))
                return x;
        }
        return null;
    }

    protected Document initializeSkeletonFile(final String skeletonFileName) throws MojoFailureException {
        // Is there a skeleton file?
        Document doc;
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
        } catch (final Exception e) {
            throw new MojoFailureException("Cannot initialize skeleton XML", e);
        }
    }

    protected void writeXmlFile(final Document doc, final File workDirectory, final String fileName) throws MojoFailureException {
        final File destinationFile = new File(workDirectory, fileName);
        try {
            final FileOutputStream ostream = new FileOutputStream(destinationFile);
            final DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
            final LSSerializer lsSerializer = domImplementation.createLSSerializer();
            lsSerializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);

            final LSOutput lsOutput = domImplementation.createLSOutput();
            lsOutput.setByteStream(ostream);
            lsSerializer.write(doc, lsOutput);

            ostream.close();
            refreshEclipse(destinationFile);
        } catch (final Exception e) {
            throw new MojoFailureException("Cannot write output file", e);
        }
    }

    protected void writeXmlFile(final String content, final File workDirectory, final String fileName) throws MojoFailureException {
        final File destinationFile = new File(workDirectory, fileName);

        try {
            final FileOutputStream ostream = new FileOutputStream(destinationFile);
            ostream.write(content.getBytes(Charset.forName(encoding)));
            ostream.close();
            refreshEclipse(destinationFile);
        } catch (final Exception e) {
            throw new MojoFailureException("Cannot write output file", e);
        }
    }

    private void refreshEclipse(final File file) {
        if (buildContext != null && file != null && file.exists()) {
            if (verbose)
                getLog().debug("refresh for build-context with class <" + buildContext.getClass().getName() + ">");
            buildContext.refresh(file); // inform Eclipse-Workspace about file-modifications
        } else
            getLog().warn("No build-context available!");
    }

    /**
     * method to convert Document to String
     *
     * @param doc
     * @return
     */
    protected String getStringFromDocument(final Document doc) {
        try {
            final StringWriter writer = new StringWriter();
            final DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
            final LSSerializer lsSerializer = domImplementation.createLSSerializer();
            lsSerializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);

            final LSOutput lsOutput = domImplementation.createLSOutput();
            lsOutput.setCharacterStream(writer);
            lsSerializer.write(doc, lsOutput);

            return writer.toString();
        } catch (final LSException ex) {
            getLog().error(ex);
            return null;
        }
    }

    protected void addResourceDir(final File resDir) {
        if (addResourceFolder && resDir != null && resDir.exists()) {
            final Resource res = new Resource();
            final Path pathResourceDir = Paths.get(resDir.toURI());
            final Path pathProject = Paths.get(project.getBasedir().toURI());
            final Path pathRelativeDir = pathProject.relativize(pathResourceDir);
            final String stringRelativeDir = FilenameUtils.separatorsToUnix(pathRelativeDir.toString());
            res.setDirectory(stringRelativeDir);
            getLog().info("Adding dir <" + resDir.getPath() + "> as relative path <" + stringRelativeDir + "> to project-resources");
            if (project != null) {
                project.addResource(res);
            } else {
                getLog().warn("No project available, adding of resource-dir skipped");
            }
        }
    }
}
