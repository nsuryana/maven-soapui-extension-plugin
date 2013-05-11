/*
 * Copyright 2013 Thomas Bouffard (redfish4ktc)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.ktc.soapui.maven.extension;

import static org.sonatype.aether.util.filter.DependencyFilterUtils.classpathFilter;

import com.eviware.soapui.tools.SoapUIMockAsWarGenerator;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;

public class MockAsWarMojo extends AbstractSoapuiRunnerMojo {
    // for 2.2.x support, see http://docs.codehaus.org/display/MAVENUSER/Mojo+Developer+Cookbook
    // http://blog.sonatype.com/people/2011/01/how-to-use-aether-in-maven-plugins/
    // http://www.jcabi.com/jcabi-aether/
    
    // TODO add a default value (build.getDirectory() + "/soapui/mock-as-war/" + "maven-artifactId.war")
    private File warFile;
    // TODO add a default value (build.getDirectory() + "/soapui/mock-as-war/" + "exploded")
    private File explodedWarDirectory;

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     * @component
     */
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     * @parameter default-value="${project.remotePluginRepositories}"
     * @readonly
     */
    private List<RemoteRepository> remoteRepos;

    @Override
    // TODO should be protected
    public void performRunnerExecute() throws MojoExecutionException, MojoFailureException {
        // List<RemoteRepository> remoteRepos = getRemoteRepos();
        getLog().info("Running Mock As War");

        SoapUIMockAsWarGenerator runner = new SoapUIMockAsWarGenerator("SoapUI Maven2 MockAsWar Runner");
        // TODO duplicate with other mojo when setting runner configuration
        runner.setProjectFile(projectFile);
        // TODO should be set to false in all runner mojo
//        runner.setEnableUI(false);
        // TODO check if this is a parameter in the abstract mojo
//        runner.setOutputFolder(null);
//        runner.setProjectPassword(projectPassword);
//        runner.setSettingsFile(settingsFile);
//        runner.setSoapUISettingsPassword(settingsPassword);
        
        try {
            buildSoapuiGuiEnvironment();

            // TODO needed as mockaswar generator does not create subdirectories
            explodedWarDirectory.mkdirs();
            runner.setOutputFolder(explodedWarDirectory.getAbsolutePath());

            // TODO needed as mockaswar generator does not create subdirectories
            warFile.getParentFile().mkdirs();
            runner.setWarFile(warFile.getAbsolutePath());

            // specific configuration
            // runner.setEnableWebUI(true);
            // runner.setLocalEndpoint(null);
            // TODO related java system properties must be correctly set (add documentation)
            // WARN the mock generator does not respect properties (it used directories in the soapui.home directory)
            // runner.setIncludeActions(false);
            // runner.setIncludeLibraries(false);
            // runner.setIncludeListeners(false);

            // TODO pro feature: include scripts

            runner.run();
        } catch (Exception e) {
            getLog().debug(e);
            throw new MojoFailureException("SoapUI has errors: " + e.getMessage(), e);
        }
    }

    // TODO extend this: add a boolean parameter to specify if jars must be downloaded
    // can create the /ext and /script (pro only?) directory
    // if system properties are not set to configure the 2 directories, set them to use directories in soapui fake. This
    // will avoid warning on soapui startup
    // also set the soapui.home property if not set
    private void buildSoapuiGuiEnvironment() throws DependencyResolutionException, IOException {
        getLog().info("Building a Soapui Gui environment");

        resolveAndCopyDependencies(new DefaultArtifact("eviware:maven-soapui-plugin:4.5.1"),
                getBuiltSoapuiGuiLibDirectory());
        // Needed, otherwise the generator mess up
        // TODO should not retrieve transitive dependencies
        // this is currently ok because it does not declare any transitive dependencies
        resolveAndCopyDependencies(new DefaultArtifact("eviware:soapui:4.5.1"), getBuiltSoapuiGuiBinDirectory());
        
        getLog().info("Soapui Gui environment built");
        
        System.setProperty("soapui.home", getBuiltSoapuiGuiBinDirectory().getAbsolutePath());
     // TODO check to set this java property if setting headless does not work:
        //uncomment to disable browser component
        //-Dsoapui.jxbrowser.disable="true"
    }

    private void resolveAndCopyDependencies(DefaultArtifact rootArtifact, File soapuiLibDirectory) throws DependencyResolutionException,
            IOException {
        getLog().info("Resolving artifact " + rootArtifact + " from " + remoteRepos);
        Collection<ArtifactResult> results = repoSystem.resolveDependencies(repoSession,
                newDependencyRequest(rootArtifact)).getArtifactResults();
        getLog().info("Artifact resolved with transitive dependencies: " + results.size());

        getLog().info("Copying artifacts to " + soapuiLibDirectory);
        for (ArtifactResult result : results) {
            File artifactFile = result.getArtifact().getFile();
            File ouputFile = new File(soapuiLibDirectory, artifactFile.getName());
            FileUtils.copyFile(artifactFile, ouputFile);
        }
        getLog().info("Copy done");
    }

    private File getBuiltSoapuiGuiLibDirectory() {
        return new File(getBuiltSoapuiGuiHomeDirectory(), "lib");
    }

    private File getBuiltSoapuiGuiBinDirectory() {
        return new File(getBuiltSoapuiGuiHomeDirectory(), "bin");
    }

    private String getBuiltSoapuiGuiHomeDirectory() {
        Build build = project.getBuild();
        return build.getDirectory() + "/soapui/home";
    }
    
    private List<RemoteRepository> getRemoteRepos() {
        // @SuppressWarnings("unchecked")
        // List<RemoteRepository> remoteRepos = project.getPluginRepositories();
        return remoteRepos;
    }

    private DependencyRequest newDependencyRequest(DefaultArtifact artifact) {
        return new DependencyRequest(newRequest(new Dependency(artifact, JavaScopes.RUNTIME)),
                classpathFilter(JavaScopes.RUNTIME));
    }

    private CollectRequest newRequest(final Dependency root) {
        final CollectRequest request = new CollectRequest();
        request.setRoot(root);
        request.setRepositories(getRemoteRepos());
        return request;
    }

}