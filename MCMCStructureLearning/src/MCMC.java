import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;


public abstract class MCMC {
	
	public static double CurrentScore;
	
	public static void main(String[] args)
	{
		if(args.length < 7)
		{
			System.out.println("Arguments required: <scoring method> <mixing steps> <running steps> <number of disease states> <number of allele codes> <data file> <output file>");
			return;
		}
		//parse data
		Parser p = new Parser();
		int[][] data = p.Parse(args[5]);
		if(data != null)
		{
			int numSNPs = data[0].length - 1;
			//parse integer arguments
			int mixingSteps = 0;
			int runningSteps = 0;
			int diseaseStates = 0;
			int alleleStates = 0;
			try{
				mixingSteps = Integer.parseInt(args[1]);
				runningSteps = Integer.parseInt(args[2]);
				diseaseStates = Integer.parseInt(args[3]);
				alleleStates = Integer.parseInt(args[4]);
			}
			catch(NumberFormatException e)
			{
				System.out.println("Can not parse integer arguments.");
				return;
			}
			//parse score function argument
			Scorer s = null;
			if(args[0].equals("AIC"))
			{
				s = new AIC(data, alleleStates, diseaseStates);
			}
			else if(args[0].equals("BIC"))
			{
				s = new BIC(data, alleleStates, diseaseStates);
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
					out = new PrintWriter(new FileWriter(args[6]));
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
		return;
	}
	
	public static int[] RunMC(int mixingSteps, int runningSteps, Scorer s, int numSNPs, int diseaseStates)
	{
		//mixingSteps is the number of steps before we start counting how many times each edge is seen.
		//runningSteps is the number of steps in which we count how many times each edge is seen.
		//returns an array indexed by snp index that gives the number of times each snp was a parent snp during the last runningSteps steps
		ArrayList<Integer> parents = new ArrayList<Integer>();
		CurrentScore = s.Score(parents);
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
		ArrayList<Integer> proposal = GenerateNeighborBN(parents, numSNPs);
		double proposalScore = s.Score(proposal);
		double R = Math.exp(proposalScore)/Math.exp(CurrentScore);
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
