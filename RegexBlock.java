/* 
**
** Copyright (c) 2014 Bill Lanahan
**
** Permission is hereby granted, free of charge, to any person obtaining a copy
** of this software and associated documentation files (the "Software"), to deal
** in the Software without restriction, including without limitation the rights
** to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
** copies of the Software, and to permit persons to whom the Software is
** furnished to do so, subject to the following conditions:
**
** The above copyright notice and this permission notice shall be included in all
** copies or substantial portions of the Software.
**
** THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
** IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
** FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
** AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
** LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
** OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
** SOFTWARE.
**
*/



/** 
 * RegexBlock - tests and collects the regexs found in a REGEX block, within a RULE block
 * @author Bill Lanahan
 */
 
package validate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/*
 * RegexBlock - holds the necessary data and methods from the REGEX block with a RULE block
 * all the lines where read in the parsing of the config file and exited for fatal errors
 */
class RegexBlock {

	private Validate.format formatValue;
	private Pattern pattern = null;
	private String delimiter = null;
	private String ruleId = null;
	private ArrayList<Pattern> patternList = null;
	private ArrayList<String> patternStringList = null;
	private final StringBuilder sb = new StringBuilder();
	private Pattern fullDelimitedLinePattern = null;
	private boolean hadFailedRegex = false;
	private Rule myRule = null;
		 
	RegexBlock () {
		// not used
	}

	RegexBlock ( Rule tmpRule, Validate.format  value,  String ruleId, String delimiter, Options ruleOption ){
		this.formatValue = value;
		this.ruleId = ruleId;
		this.delimiter = delimiter;
		this.myRule = tmpRule;
			
		// so each block can use its own macro tags in case it can interfere with data in the regex
		if ( ruleOption.getUseMacros() ) {
			Macros.setMacroFormat( ruleOption.getMacroStartString(), ruleOption.getMacroEndString(), ruleId);
		}
		
	}
	
	/* 
	 * addRegexLine - accepts a line read by the parseConfig while in a REGEX
	 * block. Comments - if specified have already been trimmed out. Here we just need
	 * to process the line as is appropriate for the file format of this rule.
	 * All tests are still static because we will not do actual validation until
	 * all the RuleIDs have been processed.
	 */
	public void addRegexLine(String line, short lineNumber, Options ruleOption, int fromLine, int toLine) {
		String[] nvArray = new String[3];
			
		 ///////
		 // jump over and see if there are macros to substitute before we save it away
		//////
		
		if ( ruleOption.getUseMacros() ) {
			line = Macros.insertMacros( line, lineNumber );
		}
					
		/* each file format has its own needs for parsing regexes */
		switch ( formatValue ) {

		/*
		 * the LINE is where we expect to try EACH regular expression against the entire line
		 * this can be used with delimiters BUT the user has to code for the delimiters in the regex(es)
		 */
		case LINE: 
		case VALUE_LINE:

			Pattern p = null;
			try {
				p = Pattern.compile( line );
			} catch ( PatternSyntaxException e ) {
				System.err.println( "\nerror: failed to compile <" + line + " > from line <" + lineNumber + ">");
				System.exit(55);
			}

			myRule.lineFormatRegexList.add( p );
			
			// only used if useRangeRestrictions == true
			myRule.toLineList.add( toLine );
			myRule.fromLineList.add( fromLine );
			
			break;

			/*
			 * the JAVA is for a java properties file
			 */
		case JAVA:
		case NAME_VALUE:

			/*
			 * line is NOT a comment but needs to be broken in name = value
			 */

			// define the hash map IF it hasn't been already
			if ( myRule.nvpHM == null ) {
				myRule.nvpHM = new HashMap<String, NVP>();
			}
						
			if ( line.indexOf( "=" ) < 0 ) {
				System.err.println("\nerror: invalid format for regex on line <" + lineNumber + "> <" + line + ">");
				hadFailedRegex = true;
				return;
			}
			
			nvArray = line.trim().split("\\s*=", 2);
						
			if ( nvArray.length > 2 ) {
				System.err.println("\nerror: invalid format for regex on line <" + lineNumber + ">  <" + line + ">");
				hadFailedRegex = true;
				return;
			} else if ( (nvArray.length == 2) && (nvArray[0] == null) && (nvArray[1] == null) ) {
				System.err.println("\nerror: invalid format for regex on line <" + lineNumber + ">, both name and value are null.");
				hadFailedRegex = true;
				return;
			} else if ( (nvArray.length == 2) && (nvArray[0] == null)  ) {
				System.err.println("\nerror: invalid format for regex on line <" + lineNumber + ">,  name is null.");
				hadFailedRegex = true;
				return; 
			} else if ( (nvArray.length == 2) && (nvArray[1] == null)  ) {
				System.err.println("\nerror: invalid format for regex on line <" + lineNumber + ">,  value is null.");
				hadFailedRegex = true;
				return;
			}
			
			// see if its a dup
		
			if ( myRule.nvpHM.containsKey( nvArray[0] ) ) {
				// we have a duplicate
				if ( ruleOption.getWarnDuplicates() ) {
					
					if ( myRule.outDups.length() > 0 ) {
						myRule.outDups.append(", ").append( nvArray[0] );
					} else {
						myRule.outDups.append( nvArray[0] );
					}
					
				}
			} 

		   	myRule.nvp = new NVP( nvArray[0], nvArray[1], lineNumber );
											
			if ( ! myRule.nvp.compilePattern() ) {
				hadFailedRegex = true;
			} else {
				// the object is done now we need to save it
				myRule.nvpHM.put( nvArray[0], myRule.nvp );
			}
			
			break;

		/*
		 * the DELIMITED file is like /etc/password, the user gives a regex to test EACH field
		 */	
		case DELIMITED:
		case VALUE_DELIMITED:

			if ( delimiter == null ) { 
				System.err.println( "\nerror: RULE with ruleID <" + ruleId + "> format=DELIMITED, so a delimiter is required.");
				System.exit( 14 );
			}

			// we just check each non-comment like and store its pattern
			try {
				// regex better have been well tested 
				pattern = Pattern.compile( line );
			} catch ( final PatternSyntaxException pe ) {
				System.err.println( "\nerror: invalid regex on line <" + lineNumber + "> " + pe.getMessage() );
				hadFailedRegex = true;
			}

			/* we'll store both the compiled pattern and its String representation for later */
			// build this structure once
			if ( patternList == null ) {
				patternList = new ArrayList<Pattern>();
			} 

			// build this structure once
			if ( patternStringList == null ) {
				patternStringList = new ArrayList<String>();
			} 

			// store compiled patterns by column position
			patternList.add( pattern );
			// store string version
			patternStringList.add( line );

			// build up a regex that will match the ENTIRE line with grouping 
			// e.g. (regex1)(delimiter) ........
			//** FUTURE: might insert non-matching group symbol - needs more thought and testing
			//sb.append("(?:").append(line).append(")(").append(delimiter).append(")");
			sb.append("(").append(line).append(")(").append(delimiter).append(")");

			break;
			
		case CUSTOM:
			// this virtually the same as NameValue - maybe condensed at some point
			/*
			 * line is NOT a comment but needs to be broken in name = value
			 */
			
			// define the hash map IF it hasn't been already
			if ( myRule.nvpHM == null ) {
				myRule.nvpHM = new HashMap<String, NVP>();
			}
			
			if ( line.indexOf( "=" ) < 0 ) {
				System.err.println("\nerror: invalid format for regex on line <" + lineNumber + "> <" + line + ">");
				hadFailedRegex = true;
				return;
			}
			
			nvArray = line.trim().split("\\s*=", 2);
						
			if ( nvArray.length > 2 ) {
				System.err.println("\nerror: invalid format for regex on line <" + lineNumber + "> <" + line + ">");
				hadFailedRegex = true;
				return;
			} else if ( (nvArray.length == 2) && (nvArray[0] == null) && (nvArray[1] == null) ) {
				System.err.println("\nerror: invalid format for regex on line <" + lineNumber + ">, both name and value are null.");
				hadFailedRegex = true;
				return;
			} else if ( (nvArray.length == 2) && (nvArray[0] == null)  ) {
				System.err.println("\nerror: invalid format for regex on line <" + lineNumber + ">,  name is null.");
				hadFailedRegex = true;
				return; 
			} else if ( (nvArray.length == 2) && (nvArray[1] == null)  ) {
				System.err.println("\nerror: invalid format for regex on line <" + lineNumber + ">,  value is null.");
				hadFailedRegex = true;
				return;
			}
			
			
			// see if its a dup
			if ( (myRule.nvpHM != null) && myRule.nvpHM.containsKey( nvArray[0] ) ) {
				// we have a duplicate
				
				if ( ruleOption.getWarnDuplicates() ) {
					
					if ( myRule.outDups.length() > 0 ) {
						myRule.outDups.append(", ").append( nvArray[0] );
					} else {
						myRule.outDups.append( nvArray[0] );
					}
					
				}
			} 
			
		   	myRule.nvp = new NVP( nvArray[0], nvArray[1], lineNumber );
		   								
			if ( ! myRule.nvp.compilePattern() ) {
				hadFailedRegex = true;
			} else {
				// the object is done now we need to save it
				myRule.nvpHM.put( nvArray[0], myRule.nvp );
			}
						
			break;
					
		default:
			System.err.println( "\nfatal: programming error this should never be called in RegexBlock.java");
			System.exit( 14 );
		}
		
		return;

	}
	

	// when the REGEX block is done, this check is called by parseConfigFile as the last test before
	// we add this regex info into the stored RULE object
	void finalRegexStaticCheck( Rule tmprule, short lineNumber ) {
		
		switch ( formatValue ) {
		case LINE:
		case VALUE_LINE:
			
			if ( hadFailedRegex ) {
				System.exit( 12 );
			}
			
			// what we were sent is supposed to be a regex so check it
			if ( myRule.lineFormatRegexList == null  ) {
				System.err.println( "\nwarning: the REGEX block ending on line <" + lineNumber + "> does NOT have any regex(es).");
			}
					
			return;
			// break; can't be reached
			
		case DELIMITED:
		case VALUE_DELIMITED:
			
			if ( hadFailedRegex ) {
				System.exit( 14 );
			}
			
			if ( patternList == null ) {
				System.err.println( "\nerror: RULE with ruleID <" + tmprule.getRuleID() + "> has <format=DELIMITED>, but no Regexes." );
				System.exit( 14 );
			}
			
			try {
				fullDelimitedLinePattern = Pattern.compile(  sb.toString() );
			} catch ( final PatternSyntaxException pse ) {
				System.err.println( "\nerror: invalid regex when all were concatenated for a full line match. " + pse.getMessage() );
				System.exit( 14 );
			}
			
			tmprule.patternListArray = patternList.toArray(); // passed as Object Array
			tmprule.patternStringListArray = patternStringList.toArray(); // passed as Object Array
			tmprule.fullDelimitedLinePattern = fullDelimitedLinePattern;
			patternList = null;
			break;
			
		case JAVA:
		case NAME_VALUE:
		case CUSTOM:
						
			if ( hadFailedRegex ) {
				System.exit( 14 );
			}
			break;
			
						
		default:
			System.err.println( "\nfatal: programming error this should never be called case in RegexBlock.java");
			System.exit( 14 );
		}
	}
}


