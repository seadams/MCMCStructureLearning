MCMCStructureLearning
=====================
This repository has one project which has three different main() methods.  How to run them and what they do is explained below.

1. MCMCStructureLearning
This runs the MCMC process on the input data using the given scoring method and creates an output file.  Th output file prints the index (0-based indexing) of the snps that were in the parent set during the sampling process.  After the snp index it prints a colon and the frequency with which the snp was in the parent set.  The program also prints the total number of snps, the average parent size and the maximum parent size to stdout.

2. PrecisionAndRecallCalc
This calculates the precision, recall, and distance to perfect precision and recall of the output from MCMCStructureLearning, given the gold standard network.  It prints these to an output file.

3. Experimenter
This runs MCMCStructureLearning and then PrecisionAndRecallCalc succesively on many data sets using the desired scoring methods (ie. it runs both programs once per scoring method on each dataset).  Each data set must be in its own folder, and all of these folders must be in one folder.  A file by each given name must be present in each data directory.  Directories with the same names as the data directories will be created (if not already present) in both output direrctories, and a file for each scoring method will be created in these directories.  The naming convention used for output files is <scoring method>[_<alpha value>], where the portion in square brackets only applies to BDeu scoring methods.  The number of mixing and running steps must be set in the code before running the program by changing the appropriate global variables.  The scoring methods used are set by setting the appropriate global booleans in the code.  The only exception is BDeu--to use this method, simply give the desired alpha values as arguments to the program.
