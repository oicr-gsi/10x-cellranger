use strict;
use Cwd;
use Data::Dumper;
use File::Copy;
use File::Find;
use Getopt::Long;

###
#  Copyright (C) 2014  Ontario Institute of Cancer Research
#
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
# Contact us:
#
#  Ontario Institute for Cancer Research
#  MaRS Centre, West Tower
#  661 University Avenue, Suite 510
#  Toronto, Ontario, Canada M5G 0A3
#  Phone: 416-977-7599
#  Toll-free: 1-866-678-6427
#  www.oicr.on.ca
###

##########
#
# Script:  sw_module_call_cellranger.pl
# Purpose: calls the Illumina tools for converting BCL files to FASTQ for one lane of sequencing.
#
#  * Means an argument is required
# Input:  BASE CALLING
#    --barcodes        : *A "+" separated list of lanes, barcodes, accessions, sample names which are separated by comma.
#                        Used in the Sample sheet and in the final file name
#    --bcl2fastqpath    : *the path containing the bcl2fastq binary to use (must be named bcl2fastq)
#    --cellranger      : *the path to cellranger binary
#    --flowcell        : *the name of the flowcell (sequencer run)
#    --help                      : flag to print usage
#    --lane            : *the lane number to perform CellRanger on
#    --use-bases-mask  : *a bases mask to pass to CellRanger
#    --run-folder      : *a run folder from a sequencing run
#
# Output:    a directory with the results from cellranger
#
##########

my (
    $run_folder,    $flowcell,             $lane,
    $barcodes,      $cellranger,           $usebasesmask,
    $bcl2fastqpath, $sample_sheet_version, $qc,
    $outdir,        $memory,               $help,
    $extra_args
);
$help       = 0;
$extra_args = "";
my $argSize      = scalar(@ARGV);
my $getOptResult = GetOptions(
    'extra-args=s'     => \$extra_args,
    'run-folder=s'     => \$run_folder,
    'cellranger=s'     => \$cellranger,
    'flowcell=s'       => \$flowcell,
    'barcodes=s'       => \$barcodes,
    'use-bases-mask=s' => \$usebasesmask,
    'bcl2fastqpath=s'  => \$bcl2fastqpath,
    'outdir=s'         => \$outdir,
    'sheet-version=i'  => \$sample_sheet_version,
    'memory=i'         => \$memory,
    'qc'               => \$qc,
    'help'             => \$help
);
usage() if ( !$getOptResult || $help );
usage()
  if ( not defined $cellranger
    || not defined $run_folder
    || not defined $flowcell
    || not defined $barcodes );
###########################################################################################################################

open OUT, ">metadata_${flowcell}.csv"
  or die "Can't open file metadata_${flowcell}.csv";
if ( $sample_sheet_version == 1 ) {
    print OUT "[Data]\n";
}
print OUT "Lane,Sample,Index";
if ( $sample_sheet_version == 1 ) {
    print OUT ",Sample_project";
}
print OUT "\n";
my @barcode_arr = split /\+/, $barcodes;
foreach my $barcode_record (@barcode_arr) {
    my @barcode_record_arr = split /,/, $barcode_record;
    my $lane               = $barcode_record_arr[0];
    my $barcode            = $barcode_record_arr[1];
    my $ius_accession      = $barcode_record_arr[2];
    my $ius_ass_sample_str = $barcode_record_arr[3];
    my $sample_id = "SWID_$ius_accession\_$ius_ass_sample_str\_$flowcell";
    print OUT "$lane,$sample_id,$barcode";
    if ( $sample_sheet_version == 1 ) {
        print OUT ",";
    }
    print OUT "\n";
}
close OUT;

my $lockFile = "${flowcell}/_lock";
if ( -e $lockFile ) {
    unlink $lockFile;
}

my $cmd =
"$cellranger mkfastq --localcores=1 --localmem=$memory $extra_args --ignore-dual-index --run $run_folder --csv metadata_${flowcell}.csv";
if ($qc) {
    $cmd .= " --qc";
}
if ( $usebasesmask && $usebasesmask ne "" ) {
    $cmd .= " --use-bases-mask $usebasesmask";
}
print "Running: $cmd\n";
$ENV{'PATH'} = $ENV{'PATH'} . ":" . $bcl2fastqpath;
my $result = system($cmd);
if ( $result != 0 ) { print "Errors! exit code: $result\n"; exit(1); }

my @dirs = ($outdir);
my %moves;
File::Find::find(
    sub {
        if ( $_ =~ /^(SWID_.*)_S\d+(.*\.fastq.gz)/ ) {
            $moves{$File::Find::name} = $outdir . $1 . $2;
        }
    },
    @dirs
);

my $cwd = cwd();
print "Current directory is $cwd\n";
foreach my $src ( keys %moves ) {
    my $dest = $moves{$src};
    print "Renaming $src to $dest\n";
    rename( $src, $dest )
      or die "Move failed: $!";
}

exit(0);

###########################################################################################################################

sub usage {
    print "Unknown option: @_\n" if (@_);
    print
"usage: sw_module_call_cellranger.pl --run-folder IlluminaRunFolder --cellranger path_to_cellranger --flowcell flowcell_name --barcodes 1,AATC,121212+1,AATG,1238291 --memory mem_in_gb [[--help|-?]\n";
    exit(1);
}

