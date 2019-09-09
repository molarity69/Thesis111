package ca.pfv.spmf.algorithms.timeseries.sax;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

import ca.pfv.spmf.algorithms.timeseries.TimeSeries;
import ca.pfv.spmf.algorithms.timeseries.reader_writer.AlgoTimeSeriesReader;
import georgiou.thesis.MainActivity;

/**
 * Example of how to use SAX algorithm for converting a time series from the source code.
 * @author Philippe Fournier-Viger, 2016.
 */
public class MainTestConvertTimeSeriesFiletoSequenceFileWithSAX {

	public static void main(String [] arg) throws IOException{
		
		// the input file
		String input = MainActivity.baseDir + File.separator + "saxInput.txt";

		// Parameters of the algorithm
		String separator = ",";
		
		// Applying the  algorithm
		AlgoTimeSeriesReader reader = new AlgoTimeSeriesReader();
		List<TimeSeries> timeSeries = reader.runAlgorithm(input, separator);
		reader.printStats();
		
		// the output file
		String output = MainActivity.baseDir + File.separator + "saxOutput.txt";//".//output.txt";

		// Parameters of the algorithm
		int numberOfSegments = 10;
		int numberOfSymbols = 7;
		
		// Set this variable to true to not apply PAA before SAX
		boolean deactivatePAA = false;
		
		// Applying the  algorithm
		AlgoConvertTimeSeriesFileToSequencesWithSAX algorithm = new AlgoConvertTimeSeriesFileToSequencesWithSAX();
		algorithm.runAlgorithm(timeSeries, output, numberOfSegments, numberOfSymbols, deactivatePAA);
		algorithm.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestConvertTimeSeriesFiletoSequenceFileWithSAX.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
