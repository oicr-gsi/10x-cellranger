/**
 *  Copyright (C) 2014  Ontario Institute of Cancer Research
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
 *  Ontario Institute for Cancer Research  
 *  MaRS Centre, West Tower
 *  661 University Avenue, Suite 510
 *  Toronto, Ontario, Canada M5G 0A3
 *  Phone: 416-977-7599
 *  Toll-free: 1-866-678-6427
 *  www.oicr.on.ca
**/

package ca.on.oicr.pde.workflows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holds all of the information for a particular IUS, including the lane number
 * and accession, IUS barcode and accession, sample name and group id, if it
 * exists. Also provides several static utility methods to process and calculate
 * over lists of ProcessEvents.
 * 
 * @author mlaszloffy
 */
public class ProcessEvent {

	private final String laneNumber;
	private final String barcode;
	private final String iusSwAccession;
	private final String sampleName;
	private final String groupId;

	public ProcessEvent(String laneNumber, String barcode, String iusSwAccession, String sampleName, String groupId) {
		this.laneNumber = laneNumber;
		this.barcode = barcode;
		this.iusSwAccession = iusSwAccession;
		this.sampleName = sampleName;
		this.groupId = groupId;
	}

	public String getLaneNumber() {
		return laneNumber;
	}

	public String getBarcode() {
		return barcode;
	}

	public String getIusSwAccession() {
		return iusSwAccession;
	}

	public String getSampleName() {
		return sampleName;
	}

	public String getGroupId() {
		return groupId;
	}

	@Override
	public String toString() {
		return String.format("[%s, %s, %s, %s, %s]", laneNumber, barcode, iusSwAccession, sampleName, groupId);
	}

	public static List<ProcessEvent> parseLanesString(String lanes) {
		List<ProcessEvent> result = new ArrayList<ProcessEvent>();
		for (String b : Arrays.asList(lanes.split("\\+"))) {
			String[] barcodeAttrs = b.split(",");
			String laneNumber = barcodeAttrs[0];
			String barcode = barcodeAttrs[1];
			String iusSwAccession = barcodeAttrs[2];
			String sampleName = barcodeAttrs.length > 3 ? barcodeAttrs[3] : "";
			String groupId = barcodeAttrs.length > 4 ? barcodeAttrs[4] : "NoGroup";
			result.add(new ProcessEvent(laneNumber, barcode, iusSwAccession, sampleName, groupId));
		}
		return result;
	}

	public static String getBarcodesStringFromProcessEventList(List<ProcessEvent> ps) {
		StringBuilder sb = new StringBuilder();
		for (ProcessEvent p : ps) {
			if (p.getBarcode().equals("NoIndex"))
				continue;
			sb.append(p.getLaneNumber()).append(",").append(p.getBarcode()).append(",").append(p.getIusSwAccession())
					.append(",").append(p.getSampleName()).append("_").append(p.getGroupId());
			sb.append("+");
		}
		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}

	public static boolean containsBarcode(List<ProcessEvent> ps, String barcode) {

		for (ProcessEvent p : ps) {
			if (p.getBarcode().equals(barcode)) {
				return true;
			}
		}

		return false;

	}

}
