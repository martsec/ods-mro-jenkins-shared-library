import org.ods.parser.JUnitParser
import org.ods.phase.PipelinePhases
import org.ods.service.DocGenService
import org.ods.service.JiraService
import org.ods.service.NexusService
import org.ods.service.ServiceRegistry
import org.ods.usecase.JUnitTestReportsUseCase
import org.ods.usecase.JiraUseCase
import org.ods.usecase.LeVaDocumentUseCase
import org.ods.util.PipelineUtil

def call(Map project, List<Set<Map>> repos) {
    def docGen  = ServiceRegistry.instance.get(DocGenService.class.name)
    def jira    = ServiceRegistry.instance.get(JiraUseCase.class.name)
    def junit   = ServiceRegistry.instance.get(JUnitTestReportsUseCase.class.name)
    def levaDoc = ServiceRegistry.instance.get(LeVaDocumentUseCase.class.name)
    def nexus   = ServiceRegistry.instance.get(NexusService.class.name)
    def util    = ServiceRegistry.instance.get(PipelineUtil.class.name)

    def testResults = [
        testsuites: Collections.synchronizedList([])
    ]

    def groups = util.prepareExecutePhaseForReposNamedJob(PipelinePhases.TEST_PHASE, repos) { script, repo ->
        def testReportsPath = "junit/${repo.id}"

        // Unstash JUnit test reports into path
        echo "Collecting JUnit XML Reports for ${repo.id}"
        junit.unstashTestReportsIntoPath("test-reports-junit-xml-${repo.id}-${script.env.BUILD_ID}", "${script.WORKSPACE}/${testReportsPath}")

        // Report JUnit test reports to Jenkins
        echo "Reporting JUnit XML Reports for ${repo.id} into Jenkins"
        junit.reportTestReportsFromPathToJenkins(testReportsPath)

        // Load JUnit test report files from path
        def testReportFiles = junit.loadTestReportsFromPath("${script.WORKSPACE}/${testReportsPath}")

        // Parse JUnit test report files into a report
        def testReport = junit.parseTestReportFiles(testReportFiles)

        // Create and store a Development Test Report document
        echo "Creating and archiving a Development Test Report for ${repo.id}"
        levaDoc.createDTR(script.env.version, project, repo, testReport, testReportFiles)

        // Add the report's test results into a global data structure
        testResults.testsuites.addAll(testReport.testsuites)
    }

    // Execute phase for groups of independent repos
    groups.each { group ->
        parallel(group)
    }

    // Report test results to corresponding test cases in Jira
    jira.reportTestResultsForProject(project.id, testResults)
}

return this
