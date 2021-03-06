import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;

/**
 * 
 * This implements the MCMC search method described in "Genetic studies of complex human diseases: Characterizing SNP-disease associations
 * using Bayesian networks."  However, we have expanded it to use various scoring systems that calculate the probability of the data given 
 * the network structure.
 *
 */
public abstract class MCMC {
	
	public static double CurrentScore;
	
	public static void main(String[] args)
	{
		//get the start time so we know how long it takes
		long start = System.currentTimeMillis();
		
		if(args.length < 9)
		{
			System.out.println("Arguments required: <scoring method> <mixing steps> <running steps> <number of disease states> <number of allele codes (including \"?\" for missing data)> <data files> <use first line: t/f> <output file> <alpha>");
			return;
		}
		//parse data
		boolean useFirstLine;
		String useFirst = args[args.length-3];
		if(useFirst.equals("t") || useFirst.equals("T") || useFirst.equals("true") || useFirst.equals("True"))
		{
			useFirstLine = true;
		}
		else if(useFirst.equals("f") || useFirst.equals("F") || useFirst.equals("false") || useFirst.equals("False"))
		{
			useFirstLine = false;
		}
		else
		{
			System.out.println("Can not parse t/f argument for using first line.");
			return;
		}			
		//parse integer arguments
		int mixingSteps = 0;
		int runningSteps = 0;
		int diseaseStates = 0;
		int alleleStates = 0;
		double alpha = 0;
		
		try{
			mixingSteps = Integer.parseInt(args[1]);
			runningSteps = Integer.parseInt(args[2]);
			diseaseStates = Integer.parseInt(args[3]);
			alleleStates = Integer.parseInt(args[4]);
			alpha = Double.parseDouble(args[args.length-1]);
		}
		catch(NumberFormatException e)
		{
			System.out.println("Can not parse integer arguments.");
			return;
		}
		Parser p = new Parser();
		String[] files = new String[args.length-8];
		for(int i = 5; i < args.length-3;i++)
		{
			files[i-5] = args[i];
		}
		int[][] data = p.Parse(files, useFirstLine, alleleStates);
		if(data != null)
		{
			System.out.println("Number of snps: "+(data[0].length - 1));
			int numSNPs = data[0].length - 1;
			//parse score function argument
			Scorer s = null;
			if(args[0].equals("AIC"))
			{
				s = new AIC(data, alleleStates, diseaseStates, alpha);
			}
			else if(args[0].equals("BIC"))
			{
				s = new BIC(data, alleleStates, diseaseStates, alpha);
			}
			else if(args[0].equals("BDeu"))
			{
				s = new BDeu(data, alleleStates, diseaseStates, alpha);
			}
			else if(args[0].equals("LogBDeu"))
			{
				s = new LogBDeu(data, alleleStates, diseaseStates, alpha);
			}
			else if(args[0].equals("SupMax"))
			{
				s = new SupMax(data, alleleStates, diseaseStates, alpha);
			}
			else if(args[0].equals("EpiScore"))
			{
				s = new EpiScore(data, alleleStates, diseaseStates, alpha);
			}
			else if(args[0].equals("Modified"))
			{
				s = new EpiScoreModified(data, alleleStates, diseaseStates, alpha);
			}
			else if(args[0].equals("Random"))
			{
				s = new RandomScorer(data, alleleStates, diseaseStates, alpha);
			}
			else
			{
				System.out.println("Scoring method does not exist.");
				return;
			}
			if(mixingSteps >= 0 && runningSteps > 0 && diseaseStates > 1)
			{
				//calculate posterior probability of each edge and print to output file
				PrintWriter out;
				try 
				{
					out = new PrintWriter(new FileWriter(args[args.length-2]));
					int[] snpCounts = RunMC(mixingSteps, runningSteps, s, numSNPs, diseaseStates);
					for(int i = 0; i < snpCounts.length; i++)
					{
						//print out snps that have a posterior probability greater than 0
						if(snpCounts[i] > 0)
						{
							out.println(Integer.toString(i) + ": " + Double.toString((double)snpCounts[i]/runningSteps));
						}
					}
					out.close();
				} 
				catch (IOException e) 
				{
					System.out.println("Can not write to file.");
				} 
			}
		}
		long end = System.currentTimeMillis();
		System.out.println((end - start) + " ms");
		return;
	}
	
	public static int[] RunMC(int mixingSteps, int runningSteps, Scorer s, int numSNPs, int diseaseStates)
	{
		//mixingSteps is the number of steps before we start counting how many times each edge is seen.
		//runningSteps is the number of steps in which we count how many times each edge is seen.
		//returns an array indexed by snp index that gives the number of times each snp was a parent snp during the last runningSteps steps
		ArrayList<Integer> parents = new ArrayList<Integer>();
		CurrentScore = s.score(parents);
		for(int i = 0; i < mixingSteps; i++)
		{
			parents = MCMCstep(parents, s, numSNPs, diseaseStates);
		}
		int[] snpCounts = new int[numSNPs];
		double averageParentSize = 0;
		int maxParentSize =0;
		for(int i=0; i < runningSteps; i++)
		{
			parents = MCMCstep(parents, s, numSNPs, diseaseStates);
			averageParentSize += parents.size();
			if(maxParentSize < parents.size())
			{
				maxParentSize = parents.size();
			}
			java.util.Iterator<Integer> itr = parents.iterator();
			while(itr.hasNext()) 
			{
		        snpCounts[itr.next().intValue()]++;
			}
		}
		System.out.println("Average parent size: " + averageParentSize/runningSteps);
		System.out.println("Max parent size: " + maxParentSize);
		return snpCounts;
		
	}
	
	public static ArrayList<Integer> MCMCstep(ArrayList<Integer> parents, Scorer s, int numSNPs, int diseaseStates)
	{
		//proposes a uniformly random move from all possible moves from current BN
		//accepts proposal with probability min{1,R} where R = P(D|proposal)/P(D|current BN)
		//returns the BN generated by accepting (or not) the proposal
		ArrayList<Integer> proposal = GenerateNeighborBNCapped(parents, numSNPs);
		double proposalScore = s.score(proposal);
		double R = s.getProbOfData(proposalScore)/s.getProbOfData(CurrentScore);
		double testVal = Math.random();
		if(testVal <= R)
		{
			CurrentScore = proposalScore;
			return proposal;
		}
		else
		{
			return parents;
		}
	}

	private static ArrayList<Integer> GenerateNeighborBNCapped(
			ArrayList<Integer> parents, int numSNPs) {
		if(parents.size() < 4)
		{
			return GenerateNeighborBN(parents, numSNPs);
		}
		else
		{ //propose removing a SNP
			ArrayList<Integer> neighbor = new ArrayList<Integer>();
			neighbor.addAll(parents);
			Random randomGenerator = new Random();
			int i = randomGenerator.nextInt(4); //random number in [0,4-1]
			neighbor.remove(i);
			return neighbor;
		}
	}

	public static ArrayList<Integer> GenerateNeighborBN(ArrayList<Integer> parents, int numSNPs)
	{
		//returns a BN that is 1 move away from the given BN, where a move is the addition or removal of an edge (parent node)
		//the BN returned is chosen uniformly at random from all such BNs.
		ArrayList<Integer> neighbor = new ArrayList<Integer>();
		neighbor.addAll(parents);
		Random randomGenerator = new Random();
		int i = randomGenerator.nextInt(numSNPs); //random number in [0,N-1]
		Integer I = Integer.valueOf(i);
		if(parents.contains(I))
		{
			neighbor.remove(I);
		}
		else
		{
			neighbor.add(I);
		}
		return neighbor;
	}
	


}
