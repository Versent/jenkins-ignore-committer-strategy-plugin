package au.com.versent.jenkins.plugins.ignoreCommitterStrategy;


import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import jenkins.branch.BranchBuildStrategy;
import jenkins.branch.BranchBuildStrategyDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMHead;

import java.util.List;
import java.util.logging.Logger;

import jenkins.scm.api.SCMFileSystem;
import jenkins.plugins.git.GitSCMFileSystem;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import jenkins.scm.api.SCMRevision;
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import java.util.logging.Level;

import java.util.Arrays;
import java.util.stream.Collectors;

public class IgnoreCommitterStrategy extends BranchBuildStrategy {
    private static final Logger LOGGER = Logger.getLogger(IgnoreCommitterStrategy.class.getName());
    private final String ignoredAuthors;
    private final Boolean allowBuildIfNotExcludedAuthor;

    @DataBoundConstructor
    public IgnoreCommitterStrategy(String ignoredAuthors, Boolean allowBuildIfNotExcludedAuthor) {
        this.ignoredAuthors = ignoredAuthors;
        this.allowBuildIfNotExcludedAuthor = allowBuildIfNotExcludedAuthor;
    }

    /**
     * Get comma-separated list of ignored commit authors
     *
     * @return comma separated list of ignored authors
     */
    public String getIgnoredAuthors() {
        return ignoredAuthors;
    }

    /**
     * Determine if build is allowed if at least one author in the changeset is not excluded
     * @return indicates if build should be triggered if one of the authors is not in the exclude list
     */
    public Boolean getAllowBuildIfNotExcludedAuthor() { return allowBuildIfNotExcludedAuthor; }

    /**
     * Determine if build is required by checking if any of the commit authors is in the ignore list
     * and/or if changesets with at least one non excluded author are allowed
     * <p>
     * {@inheritDoc}
     *
     * @return true if changeset does not have commits by ignored users or at least one user is not excluded and {allowBuildIfNotExcludedAuthor} is true
     */
    @Override
    public boolean isAutomaticBuild(SCMSource source, SCMHead head, SCMRevision currRevision, SCMRevision prevRevision) {
        GitSCMFileSystem.Builder builder = new GitSCMFileSystem.BuilderImpl();

        try {
            SCMFileSystem fileSystem = builder.build(source, head, currRevision);

            if (fileSystem == null) {
                LOGGER.log(Level.SEVERE, "Error retrieving SCMFileSystem");
                return true;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fileSystem.changesSince(prevRevision, out);
            GitChangeLogParser parser = new GitChangeLogParser(true);

            List<GitChangeSet> logs = parser.parse(new ByteArrayInputStream(out.toByteArray()));
            List<String> ignoredAuthorsList = Arrays.stream(
                    ignoredAuthors.split(",")).map(e -> e.trim().toLowerCase()).collect(Collectors.toList());

            LOGGER.info(String.format("Ignored authors: %s", ignoredAuthorsList.toString()));

            for (GitChangeSet log : logs) {
                String authorEmail = log.getAuthorEmail().trim().toLowerCase();
                Boolean isIgnoredAuthor = ignoredAuthorsList.contains(authorEmail);

                if (isIgnoredAuthor) {
                    if (!allowBuildIfNotExcludedAuthor) {
                        // if author is ignored and changesets with at least one non-excluded author are not allowed
                        LOGGER.info(String.format(
                                "Changeset contains ignored author %s (%s), and allowBuildIfNotExcludedAuthor is %s, therefore build is not required",
                                authorEmail, log.getCommitId(), allowBuildIfNotExcludedAuthor));
                        return false;
                    }

                } else {
                    if (allowBuildIfNotExcludedAuthor) {
                        // if author is not ignored and changesets with at least one non-excluded author are allowed
                        LOGGER.info(String.format(
                                "Changeset contains non ignored author %s (%s) and allowIfNotExcluded is %s, build is required",
                                authorEmail, log.getCommitId(), allowBuildIfNotExcludedAuthor));
                        return true;
                    }
                }
            }
            // here if commits are made by ignored authors and allowBuildIfNotExcludedAuthor is true, in this case return false
            // or if all commits are made by non-ignored authors and allowBuildIfNotExcludedAuthor is false, in this case return true
            LOGGER.info(String.format("All commits in the changeset are made by %s excluded authors, build is %s",
                    allowBuildIfNotExcludedAuthor ? "" : "Non", !allowBuildIfNotExcludedAuthor ));

            return !allowBuildIfNotExcludedAuthor;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception", e);
            return true;
        }

    }

    @Extension
    public static class DescriptorImpl extends BranchBuildStrategyDescriptor {
        public String getDisplayName() {
            return "Ignore Committer Strategy";
        }
    }

}
