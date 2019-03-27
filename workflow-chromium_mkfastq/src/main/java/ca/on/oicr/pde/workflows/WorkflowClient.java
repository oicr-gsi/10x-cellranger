/**
 *  Copyright (C) 2018  Ontario Institute of Cancer Research
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact us:
 *
 * Ontario Institute for Cancer Research
 * MaRS Centre, West Tower
 * 661 University Avenue, Suite 510
 * Toronto, Ontario, Canada M5G 0A3
 * Phone: 416-977-7599
 * Toll-free: 1-866-678-6427
 * www.oicr.on.ca
 *
 */
package ca.on.oicr.pde.workflows;

import ca.on.oicr.pde.utilities.workflows.OicrWorkflow;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.sourceforge.seqware.pipeline.workflowV2.model.Command;
import net.sourceforge.seqware.pipeline.workflowV2.model.Job;
import net.sourceforge.seqware.pipeline.workflowV2.model.SqwFile;

/**
 * Workflow for cellranger. See the README for more information.
 */
public class WorkflowClient extends OicrWorkflow {

	private String binDir;
	private String swModuleCallCellRanger;
	private String runFolder;
	private String flowcell;
	private String lanes;
	private String cellranger;
	private String runName;
	private String memory;
	private String packagerMemory;
	private String queue;
	private Integer sheetVersion;
	private Integer readEnds;
	private String usebasesmask;
	private String bcl2fastqpath;
	private boolean manualOutput;

	private void WorkflowClient() {

		binDir = getWorkflowBaseDir() + "/bin/";
		bcl2fastqpath = getProperty("bcl2fastq_path");
		swModuleCallCellRanger = binDir + "sw_module_call_cellranger.pl";
		runFolder = getProperty("run_folder");
		flowcell = getProperty("flowcell");
		readEnds = Integer.parseInt(getProperty("read_ends"));
		lanes = getProperty("lanes");
		cellranger = getProperty("cellranger");
		sheetVersion = Integer.parseInt(getProperty("sample_sheet_version"));
		runName = getProperty("run_name");
		memory = getProperty("memory");
		packagerMemory = getProperty("packager_memory");
		queue = getOptionalProperty("queue", "");
		usebasesmask = getOptionalProperty("use_bases_mask", "");
		manualOutput = Boolean.valueOf(getOptionalProperty("manual_output", "false"));
	}

	@Override
	public void setupDirectory() {

		WorkflowClient(); // constructor hack
	}

	@Override
	public Map<String, SqwFile> setupFiles() {

		return this.getFiles();

	}

	@Override
	public void buildWorkflow() {

		List<ProcessEvent> ls = ProcessEvent.parseLanesString(lanes);

		final String lane = ls.stream().map(ProcessEvent::getLaneNumber).distinct().sorted().collect(Collectors.joining("_"));
		final List<String> parents = ls.stream().filter(pe -> pe.getBarcode().equals("NoIndex")).map(ProcessEvent::getIusSwAccession).collect(Collectors.toList());

		Job zipReportsJob = getZipJob(getFastqPath(flowcell) + "/Reports/html/", "Reports_" + runName + "_" + lane  + ".zip", parents);
		zipReportsJob.setMaxMemory(packagerMemory).setQueue(queue);
		Job zipStatsJob = getZipJob(getFastqPath(flowcell) + "/Stats/", "Stats_" + runName + "_" + lane  + ".zip", parents);
		zipStatsJob.setMaxMemory(packagerMemory).setQueue(queue);

		Job cellRangerJob = getCellRangerJob(ls);
		cellRangerJob.setMaxMemory(memory).setQueue(queue);
		zipReportsJob.addParent(cellRangerJob);
		zipStatsJob.addParent(cellRangerJob);
	}

	private Job getCellRangerJob(List<ProcessEvent> ps) {

		String barcodes = ProcessEvent.getBarcodesStringFromProcessEventList(ps);

		// NOTE: newJob adds autoincrement counter to job name
		Job job = newJob("ID10_CellRanger");
		Command c = job.getCommand();
		c.addArgument("perl").addArgument(swModuleCallCellRanger);
		c.addArgument("--run-folder " + runFolder);
		c.addArgument("--cellranger " + cellranger);
		c.addArgument("--flowcell " + flowcell);
		c.addArgument("--barcodes " + barcodes);
		c.addArgument("--sheet-version " + sheetVersion);
		c.addArgument("--bcl2fastqpath " + bcl2fastqpath);
		// We only give 80% of the memory to Cell Ranger to give it overhead for things like when the Python interpreter forks
		c.addArgument("--memory " + (Integer.parseInt(memory) * 80 / 102400));
		if (usebasesmask != null && !usebasesmask.isEmpty()) {
			c.addArgument("--use-bases-mask " + usebasesmask);
		}

		// Temporary workaround until https://jira.oicr.on.ca/browse/SEQWARE-1895 is
		// fixed
		c.addArgument("2>stderr.log");

		// for each sample sheet entry, provision out the associated fastq(s).
		int sampleSheetRowNumber = 1;
		for (ProcessEvent p : ps) {
			if (p.getBarcode().equals("NoIndex")) {
				SqwFile r1 = createOutputFile(getUndeterminedFastqPath(flowcell, p.getLaneNumber(), "1"),
						"lane" + p.getLaneNumber() + "_Undetermined_L00" + p.getLaneNumber() + "_R1_001.fastq.gz",
						"chemical/seq-na-fastq-gzip", manualOutput);
				r1.setParentAccessions(Arrays.asList(p.getIusSwAccession()));
				job.addFile(r1);
				if (readEnds > 1) {
					SqwFile r2 = createOutputFile(getUndeterminedFastqPath(flowcell, p.getLaneNumber(), "2"),
							"lane" + p.getLaneNumber() + "_Undetermined_L00" + p.getLaneNumber() + "_R2_001.fastq.gz",
							"chemical/seq-na-fastq-gzip", manualOutput);
					r2.setParentAccessions(Arrays.asList(p.getIusSwAccession()));
					job.addFile(r2);
				}
			} else {
				SqwFile r1 = createOutputFile(
						getOutputPath(flowcell, p.getLaneNumber(), p.getIusSwAccession(), p.getSampleName(),
								p.getBarcode(), "1", p.getGroupId(), sampleSheetRowNumber),
						generateOutputFilename(runName, p.getLaneNumber(), p.getIusSwAccession(), p.getSampleName(),
								p.getBarcode(), "1", p.getGroupId()),
						"chemical/seq-na-fastq-gzip", manualOutput);
				r1.setParentAccessions(Arrays.asList(p.getIusSwAccession()));
				job.addFile(r1);

				if (readEnds > 1) {
					SqwFile r2 = createOutputFile(
							getOutputPath(flowcell, p.getLaneNumber(), p.getIusSwAccession(), p.getSampleName(),
									p.getBarcode(), "2", p.getGroupId(), sampleSheetRowNumber),
							generateOutputFilename(runName, p.getLaneNumber(), p.getIusSwAccession(),
									p.getSampleName(), p.getBarcode(), "2", p.getGroupId()),
							"chemical/seq-na-fastq-gzip", manualOutput);
					r2.setParentAccessions(Arrays.asList(p.getIusSwAccession()));
					job.addFile(r2);
				}

				sampleSheetRowNumber++;
			}
		}

		return job;

	}

	private Job getZipJob(String inputDirectoryPath, String outputFileName, List<String> parentIUSes) {

		String outputZipFilePath = inputDirectoryPath + "/" + outputFileName;

		Job job = newJob("Create_" + outputFileName);

		Command c = job.getCommand();
		c.addArgument("cd " + inputDirectoryPath + " &&");
		c.addArgument("zip -r");
		c.addArgument(outputFileName);
		c.addArgument("."); // zip all files in current directory ("inputDirectoryPath")

		SqwFile f = createOutputFile(outputZipFilePath, "application/zip-report-bundle", manualOutput);
    f.setParentAccessions(parentIUSes);
		job.addFile(f);

		return job;

	}

	public static String generateOutputFilename(String runName, String laneNum, String iusSwAccession,
			String sampleName, String barcode, String read, String groupId) {
		StringBuilder o = new StringBuilder();
		o.append("SWID_");
		o.append(iusSwAccession).append("_");
		o.append(sampleName).append("_");
		o.append(groupId).append("_");
		o.append(runName).append("_");
		o.append(barcode).append("_");
		o.append("L00").append(laneNum).append("_");
		o.append("R").append(read).append("_");
		o.append("001.fastq.gz");
		return o.toString();
	}

	public static String getOutputPath(String flowcell, String laneNum, String iusSwAccession, String sampleName,
			String barcode, String read, String groupId, int sampleSheetRowNumber) {
		StringBuilder o = new StringBuilder();
		o.append(getFastqPath(flowcell));
		o.append(flowcell).append("/");
		o.append("SWID_").append(iusSwAccession).append("_").append(sampleName).append("_").append(groupId).append("_")
				.append(flowcell).append("/");
		o.append("SWID_").append(iusSwAccession).append("_").append(sampleName).append("_").append(groupId).append("_")
				.append(flowcell).append("_");
		o.append("S").append(sampleSheetRowNumber).append("_");
		o.append("L00").append(laneNum).append("_");
		o.append("R").append(read).append("_001.fastq.gz");

		return o.toString();
	}

	public static String getUndeterminedFastqPath(String flowcell, String laneNum, String read) {
		StringBuilder o = new StringBuilder();
		o.append(getFastqPath(flowcell));
		o.append("Undetermined_S0_");
		o.append("L00").append(laneNum).append("_");
		o.append("R").append(read).append("_001.fastq.gz");

		return o.toString();
	}

	public static String getFastqPath(String flowcell) {
		StringBuilder o = new StringBuilder();
		o.append(flowcell).append("/outs/fastq_path/");

		return o.toString();
	}

	protected SqwFile createOutputFile(String workingPath, String outputFileName, String metatype,
			boolean manualOutput) {
		SqwFile file = new SqwFile();
		file.setForceCopy(true);
		file.setIsOutput(true);
		file.setSourcePath(workingPath);
		file.setType(metatype);

		StringBuilder builder = new StringBuilder();
		builder.append(this.getMetadata_output_file_prefix()).append(getMetadata_output_dir()).append("/");
		if (!manualOutput) {
			builder.append(this.getName()).append("_").append(this.getVersion()).append("/").append(this.getRandom())
					.append("/");
		}
		builder.append(outputFileName);

		file.setOutputPath(builder.toString());

		return file;
	}

}
