import java.util.*;
import java.io.*;

/**
 * Verbati Tagging class. Static methods to train a Hidden Markov Model for relationships between parts of 
 * speech, and then use that model to tag new sentences. Trains the model based on a file composed of sentences
 * and a file composed of their corresponding parts of speech.
 * 
 * @author John Weingart, CS10 Fall 2018
 *
 */
public class VerbatiTagging {
	static Map<String, Map<String, Double>> observations = null;
	static Map<String, Map<String, Double>> transitions = null;
	static List<List<String>> tagsEachLine = new ArrayList<List<String>>();

	static double u = -100;

	public static void main(String[] args) {
		tagsEachLine = new ArrayList<List<String>>();
		
		training("files/simple-train-sentences.txt", "files/simple-train-tags.txt"); //train model based on these two files 
		
		//these make up the hidden markov model
		System.out.println(observations);
		System.out.println(transitions);
	
		BufferedReader input = createReader("files/simple-test-sentences.txt"); //create a buffered reader
		String line = "";
		
		try {
			while((line=input.readLine()) != null) {  //loops through every line of the file, tags each word using Verbiti Tagging
				verbatiTagging(line);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//print my tags
		for(int i = 0; i < tagsEachLine.size(); i++) {
			for(int j = 0; j < tagsEachLine.get(i).size(); j++) {
				System.out.print(tagsEachLine.get(i).get(j) + " ");
			}
			System.out.println();
		}

		testing("files/brown-test-tags.txt");
		//writeTagsToFile("files/johns-tags-simple.txt");

		Scanner inputScan = new Scanner(System.in);

		System.out.println("Enter a line to tag! Or press 'q' to quit");
		String userInput = "";
		while(userInput != "q") { //each line entered by the user, runs verbatiTagging and print the tags on the next line
			tagsEachLine = new ArrayList<List<String>>(); //reset tags list
			userInput = inputScan.nextLine();
			
			if(userInput.length() > 0) 
			{
				verbatiTagging(userInput); //call verbatiTagging on line
			}
			
			if(tagsEachLine.size() > 0) { //print on next line
				for(int i = 0; i < tagsEachLine.get(0).size(); i++) {
					System.out.print(tagsEachLine.get(0).get(i) + " ");
				}
			}
			System.out.println();
		}
		inputScan.close();
	}

	/**
	 * Simply creates a buffered reader that reads "fileName" file
	 * @param fileName		file to read
	 * @return				BufferedReader
	 */
	public static BufferedReader createReader(String fileName) {
		BufferedReader input = null;
		try {
			input = new BufferedReader(new FileReader(fileName));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return input;
	}
	
	/**
	 * Writes all tags to a file, written just like the training files. Allows for easy 
	 * comparison to the correct tagging files for testing
	 * @param fileNameTags			file to write to
	 */
	public static void writeTagsToFile(String fileNameTags) {
		BufferedWriter output = null;
		try {
			output = new BufferedWriter(new FileWriter(fileNameTags));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			for(int i = 0; i < tagsEachLine.size(); i++) {
				List<String> thisList = tagsEachLine.get(i);
				for(int j = 0; j < thisList.size(); j++) {
					output.write(thisList.get(j) + " ");
				}
				output.newLine();
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	public static void testing(String fileNameCheck) {
		BufferedReader input = null;
		try {
			input = new BufferedReader(new FileReader(fileNameCheck));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String line = "";
		int countLines = 0;
		int countWrongTags = 0;
		int countWrongLines = 0;
		int countTags = 0;

		try {
			while((line = input.readLine()) != null) {
				String[] tagsOnLine = line.split(" ");
				boolean thisLineAccurate = true; //set true initially at every line
				for(int i = 0; i < tagsOnLine.length; i++) {
					if(!tagsOnLine[i].equals(tagsEachLine.get(countLines).get(i))) { //wrong tag!
						countWrongTags ++; //increment # wrong tags
						if(thisLineAccurate) { 
							countWrongLines ++; //increment # wrong lines
							thisLineAccurate = false;
						}
					}
					countTags++;
				}
				countLines ++;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("# wrong tags: " + countWrongTags + " Total tags: " + countTags + "  % wrong tags: " + ((double)countWrongTags / (double) countTags) * 100.0);
		System.out.println("\n# wrong lines: " + countWrongLines + " Total Lines: " + countLines + " % wrong lines: " + ((double)countWrongLines / (double) countLines) * 100.0);
	}

	/**
	 * Verbati Method: takes an input line, and finds its tags using the static maps 'observations' 
	 * and 'transitions' (which store the information for a HMM). 
	 * 
	 * @param line			line to tag
	 */
	public static void verbatiTagging(String line) {
		line = line.toLowerCase(); //lowercase that shit
		String[] words = line.split(" ");

		int numWords = words.length;

		//every line, we want to start at "start", with score 0.0
		List<String> currStates = new ArrayList<String>();
		currStates.add("start");
		Map<String, Double> currScores = new HashMap<String, Double>();
		currScores.put("start", 0.0);
		
		//restart nextStates to null (this will get set as we go to the first word)
		List<String> nextStates;
		Map<String, Double> nextScores = null;
		Map<String, List<String>> backTrack = new HashMap<String, List<String>>(); //will hold all backwards traces in form Map<p.o.s., list of p.o.s. leading to this p.o.s.>

		for(int i = 0; i< numWords; i++) { //loop through all words in line
			
			Map<String, List<String>> newBack = new HashMap<String, List<String>>();  //new backwards trace must be created every word (backTrack stores permanent copy)
			nextStates = new ArrayList<String>();
			nextScores = new HashMap<String, Double>();

			for(String curr: currStates) {

				Set<String> theseNextStates = null;
				if(transitions.containsKey(curr)) {
					theseNextStates = transitions.get(curr).keySet();


					for(String next: theseNextStates) {
						double nextScore = u;

						if(observations.get(next).containsKey(words[i]) && currScores.containsKey(curr)) { //if the word is found under this part of speech
							nextScore = currScores.get(curr) //current score
									+ transitions.get(curr).get(next)  //transition score
									+ observations.get(next).get(words[i]); //observation score
						}
						else if(currScores.containsKey(curr)){
							nextScore = currScores.get(curr) //current score
									+ transitions.get(curr).get(next) //transition score
									+ u; //penalty because the word has not been observed
						}

						if(!nextScores.containsKey(next) || nextScore > nextScores.get(next)) {
							nextScores.put(next, nextScore); 
							nextStates.add(next);
							if(backTrack.containsKey(curr))
							{
								List<String> thisList = backTrack.get(curr);
								List<String> newList = new ArrayList<String>();
								for(int j = 0; j < thisList.size(); j++) {
									newList.add(thisList.get(j));
								}
								newList.add(next);
								newBack.put(next, newList);
							}
							else {
								List<String> thisList = new ArrayList<String>();
								thisList.add(next);
								newBack.put(next, thisList);
							}
						}
					}
				}
			}

			currStates = nextStates;
			currScores = nextScores;
			backTrack = newBack;
		}
		double max = -1000000000;
		String maxEnd = "";
		for(String thisPOS: nextScores.keySet()) {
			if(nextScores.get(thisPOS) > max) {
				max = nextScores.get(thisPOS);
				maxEnd = thisPOS;
			}
		}
		tagsEachLine.add(backTrack.get(maxEnd));
	}


	/**
	 * Training method: takes two files: 1) the sentences and 2) part of speech tags for each word in the 
	 * sentence. It reads these files, and creates the 'observations' map that counts how often each word is a
	 * certain part of speech, and the 'transitions' map that counts how often each part of speech comes after
	 * another certain part of speech. These counts of each event are converted to the log of the probability of
	 * that event occurring.
	 * 
	 * @param fileNameSentences				file of sentences to read
	 * @param fileNameTags					file of tags to read
	 */
	public static void training(String fileNameSentences, String fileNameTags) {
		observations = new HashMap<String, Map<String, Double>>();//p.o.s.--> word --> number
		transitions = new HashMap<String, Map<String, Double>>(); //p.o.s.--> next p.o.s. --> number

		BufferedReader input = null;
		BufferedReader input2 = null;

		try {
			input = new BufferedReader(new FileReader(fileNameSentences));
			input2 = new BufferedReader(new FileReader(fileNameTags));
		} catch (FileNotFoundException e) {
			System.out.println("File not found!");
			// TODO Auto-generated catch block
		}

		String line;
		String line2;
		int count = 0;

		try {
			while((line=input.readLine()) != null && (line2 = input2.readLine()) != null) { //ensure that training tags and sentences have same # lines
				String[] words = line.split(" ");
				String[] partsOfSpeech = line2.split(" ");

				//lowercase all words
				for(int i = 0; i < words.length; i++) {
					words[i] = words[i].toLowerCase();
				}

				int numWords = Math.min(words.length, partsOfSpeech.length); //file error checking: ensures we have a part of speech for every word

				//every line, add a transition from the "." end of the last line to the first word in this line.
				if(count > 0) {
					if(!transitions.containsKey("start")) {
						Map<String, Double> thisPOS = new HashMap<String, Double>();
						thisPOS.put(partsOfSpeech[0], 1.0);
						transitions.put("start", thisPOS);
					}
					else if(!transitions.get("start").containsKey(partsOfSpeech[0])) {
						transitions.get("start").put(partsOfSpeech[0], 1.0);
					}
					else {
						Map<String, Double> thisPOS = transitions.get("start");
						double currentNum = thisPOS.get(partsOfSpeech[0]);
						thisPOS.put(partsOfSpeech[0], currentNum+1.0); //increment the count for the current word 
					}
				}

				for(int i = 0; i < numWords; i++) {

					String word = words[i];
					String thisPart = partsOfSpeech[i];

					if(!observations.containsKey(thisPart)) { //if the part of speech has not been seen
						Map<String, Double> thisPOS = new HashMap<String, Double>();
						thisPOS.put(word, 1.0);
						observations.put(thisPart, thisPOS);
					}
					else if(!observations.get(thisPart).containsKey(word)) { //the word has not been seen
						observations.get(thisPart).put(word, 1.0); 
					}
					else { //the word has been seen
						Map<String, Double> thisPOS = observations.get(thisPart);
						double currentNum = thisPOS.get(word);
						thisPOS.put(word, currentNum+1); //increment the count for the current word 
					}

				}

				for(int i = 0; i < numWords-1; i++) { 

					String thisPart = partsOfSpeech[i];
					String nextPart = partsOfSpeech[i+1];

					if(!transitions.containsKey(thisPart)) { //part of speech has not been seen
						Map<String, Double> thisTrans = new HashMap<String, Double>();
						thisTrans.put(nextPart, 1.0);
						transitions.put(thisPart, thisTrans);
					}
					else if(!transitions.get(thisPart).containsKey(nextPart)) { //transition has not been seen
						transitions.get(thisPart).put(nextPart, 1.0);
					}
					else { //transition has been seen
						Map<String, Double> thisTrans = transitions.get(thisPart);
						double currentNum = thisTrans.get(nextPart);
						thisTrans.put(nextPart, currentNum+1); //increment the count for the current p.o.s. transition
					}
				}
				count++;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Error reading file.");
			System.out.println(e.getMessage());
		}

		//convert from counts of each event to natural log of the probability
		Set<String> allPartsObs = observations.keySet();
		Set<String> allPartsTrans = transitions.keySet();

		for(String p : allPartsObs) { //run through all p.o.s.
			Map<String, Double> obs = observations.get(p);

			int sum = 0;

			Set<String> allWords = obs.keySet(); //this is the set of all words for a certain p.o.s.
			for(String w: allWords) {
				sum += obs.get(w); //get sum of counts
			}
			for(String w: allWords) {
				obs.put(w, Math.log(obs.get(w)/sum)); //replace count with log of prob of the observation
			}			
		}
		
		for(String p: allPartsTrans) {
			Map<String, Double> trans = transitions.get(p);
			Set<String> allPos = trans.keySet(); //set of all next p.o.s. for a certain p.o.s.

			int sum2 = 0;
			for(String nextPos: allPos) {
				sum2 += trans.get(nextPos); //get sum of counts
			}
			for(String nextPos: allPos) {
				trans.put(nextPos, Math.log(trans.get(nextPos)/sum2)); //replace count with log of prob of the transition
			}
		}
	}
}