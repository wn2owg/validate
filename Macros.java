
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

package validate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Macros will be instantiated with built in macros, these may be replaced or supplemented with users
 * macros in the config file MACROS block as well as a command line file
 * Macros once built are available to EVERY Rule block, the users choice per rule block if to use or not use them
 * and what frames them
 * @author Bill Lanahan
 *
 */

/*
 * This class uses 2 different patterns related to macros - so to clarify
 * patternMacroSubstitution - means how the user puts macros into a regex in a rule OR how they can be used in a macro block
 * 	- this is for parsing something like: name <zip> age, where zip is a macro framed by the start tag < and end tag >
 * 		- because the start and end tags COULD be different in different rules they need to be processed multiple times
 * 	- macroInputPattern the other pattern is for reading a macro definition in a macro file or macro block, here the format is
 * 		- macroName<one space>regex
 * 			- NOTE a regex CAN include another macro if it was already defined
 */
class Macros {
	
	private static String startTag = null;
	private static String endTag = null;
	private static Pattern patternMacroInput = null;  
	private static Matcher matcherMacroInput = null;
	private static String file = null;
	private static HashMap<String, String> macrosHM = new HashMap<String, String>();
	
	// these are shared by several methods
	private static String line = null;
	private static BufferedReader br = null;
	private static Pattern patternMacroSubstitution = null; // for <macro> kind of parse
	private static Matcher matcherMacroSubstitution = null;
	private static boolean bug = false;
	private static short macroCount = 0;
				
	/*
	 * processed macro file if it exists the macro name when in a file or block from a format like
	 * macroName\smacroValue
	 */
	Macros( String file, String start, String end ) {
		
		StringBuilder item = new StringBuilder();
		item.append( start ).append( "(" ).append("\\w+").append( ")" ).append( end );
				
		// this may be used by the macro file of macro block
		//*****
		//***** careful - we manipulate and toggle between 2 different patterns and matchers
		//*****
		try {
			patternMacroSubstitution = Pattern.compile( item.toString() );
			patternMacroInput = Pattern.compile( "^\\s*(\\w+)\\s*=(.*)$" ); 
			
		} catch (final Exception e) {
			System.err.println( "\nerror: macro failed to compile pattern.");
			System.exit( 19 );
		}
		
		if ( (file = Validate.macrosFile) != null ) {
			readMacroFile( file );
		}
		
		startTag = start;
		endTag = end;
	}
	
	static void setMacroStartTag( String tag ) {
		startTag = tag;
	}
	
	static void setMacroEndtTag( String tag ) {
		endTag = tag;
	}
	
	/*
	 * as each rule gets into its regex block, this gets set IF the option to use macros is on
	 * Note: this can change per rule block in case the macro tags could have
	 * conflicts with the data in the REGEX block - not like to be used IMHO
	 */
	static void setMacroFormat( String start,  String end, String ruleID ) {
		String suffix = null;
		
		if ( (ruleID != null) && (ruleID.length() != 0) ) {
			suffix = "in rule with id <" + ruleID + ">.";
		}
		
		if ( (start.length() == 0) || (end.length() == 0) ) {
			System.err.println( "\nfatal: macro start and end tags must be non-null " + suffix );
			System.exit( 19 );
		}
		
		if ( start.equals( end ) ) {
			System.err.println( "\nfatal: macro start and end tags must be different " + suffix );
			System.exit( 19 );
		}
		
		/*
		 * this is the LOOK of a macro with its tags and values on a line, we only want to look up a string that looks like a macro IF
		 * its in macro format like <phone_number>
		 */
		StringBuilder item = new StringBuilder();
		item.append( start ).append( "(" ).append("\\w+").append( ")" ).append( end );
										
		try {
			patternMacroSubstitution = Pattern.compile( item.toString() );
					
		} catch (final Exception e) {
			System.err.println( "\nerror: macro failed to compile pattern.");
			System.exit( 19 );
		}
		
		startTag = start; // set the private values for this class
		endTag = end;
	}
	 
		
	/* 
	 * insertMacros - receives the line for the Rule parsing, and it is parsed and has macros substituted
	 * and then return a newLine to be used
	 */
	static String insertMacros( String line, short lineNumber ) {
		/*
		 * the line passed in can have various formats: Name=Value, delimited, etc
		 * what ever it is we search for macro start and end tags and lookup the string in 
		 * the middle and do substitution if found
		 */
		StringBuilder item = new StringBuilder();
		item.append( startTag ).append( "(" ).append("\\w+").append( ")" ).append( endTag );
				
		try {
			patternMacroSubstitution = Pattern.compile( item.toString() ); // for macros within macros	
		} catch (final Exception e) {
			System.err.println( "\nerror: macro failed to compile pattern.");
			System.exit( 19 );
		}
			 				
		matcherMacroSubstitution = patternMacroSubstitution.matcher( line );
		
		// found a start tag and an end tag so do all substitutions // NOTE perfect could have < < < name >
		String value = null;
						
		while ( matcherMacroSubstitution.find() ) {
			
			if ( (value = macrosHM.get( matcherMacroSubstitution.group( 1 ))) != null ) {
				line = line.replace( matcherMacroSubstitution.group( 0 ), value);
			} else {
				System.err.println( "\nerror: macro NAME <" + matcherMacroSubstitution.group( 1 ) + "> on line <" + lineNumber + "> was never defined.");
				System.exit( 19 );
			}
					
		}
		
		return line;
	}
	
	/*
	 * reads the macrosFile from cmd line and saves in a HashMap
	 */
	static void readMacroFile( String file ) {
		line = null;
		br = null;
		short lineNumberIndex = 0;
		bug = false;
		macroCount = 0;
	
		if ( file == null ) {
			System.err.println( "\nfatal: readMacroFile called with a null file name" );
			System.exit( 100 );
		}
		
		try {
			short startLine = 0;
			boolean startedContinuation = false;
			boolean continuationState = false;
			String tmpline = null;
			final File inputFile = new File( file );
			br = new BufferedReader( new FileReader( inputFile ) );

			while ( (line = br.readLine()) != null ) {
							
				lineNumberIndex++;
					                        								
				if ( line.matches("^\\s*#.*$|^\\s*$")) {
					// comment or blank - skip it
					continue;
				}
				
				if ( line == null ) { return; }
				
				/*
				 * insert code here to see if we need to handle multi-line macros
				 * 
				 */
				
				if ( Options.getMacroContinuation() == true ) {
					
					if ( line.endsWith("\\") && ! line.endsWith("\\\\")  ) {
						
						
						if ( startedContinuation == false ) {
							startedContinuation = true;
							startLine = lineNumberIndex;
							startLine--;
						}

						// its a continuation; need to adjust line and concatenate
						if ( continuationState == false ) {
							// swallow the \
							
							continuationState = true;
							tmpline = line.substring(0, line.length() - 1 ); // trims the backslash
							continue;
						} else {
							// not the final line, so append and read more
							line = line.trim(); // swallows the start of line WS
							line = line.substring(0, line.length() - 1); // trims the backslash
							tmpline += line; 
							continue;
						}

					} else {

						if ( continuationState ) {
							// the final line now, so trim and use it
							line = line.trim();
							tmpline += line;
							continuationState = false; // reset
							line = tmpline;
						} 
					}
					
					if ( tmpline != null ) {
						line = tmpline;
						lineNumberIndex = startLine;
					}

					continuationState = false; // reset
				}
		
				/* it must be a macro line
				 * format (\\s*)(key)\\s(macro) where key is \\w+
				 */
				
				matcherMacroInput = patternMacroInput.matcher( line ); // splits the line to get LHS RHS or name value
				
				//*****
				//***** careful - we manipulate and toggle between 2 different patterns and matchers
				//*****
				if ( matcherMacroInput.find() ) {
				
					String RHS = matcherMacroInput.group( 2 ); // value
					
					// see if the RightHandSide (RHS) has macros to be expanded e.g. ADDR <CITY>,<STATE> <ZIP>
					final String LHS = matcherMacroInput.group( 1 ); // name
					
					matcherMacroSubstitution = patternMacroSubstitution.matcher( RHS );		// reusing a previous matcherMacroInput!			
					
					while ( matcherMacroSubstitution.find() ) {
						// loop since could be more than one
						// make sure key is defined
						String value;
						
						if ( (value = macrosHM.get( matcherMacroSubstitution.group( 1 )) ) != null ) {
							RHS = RHS.replace( matcherMacroSubstitution.group( 0 ) , value );
						} else {
							System.err.println( "\nfatal: a macro definition included other macros which were not defined in <" + file + "> line <" + lineNumberIndex + ">.");
							System.exit( 18 );
						}
						
						matcherMacroSubstitution.reset( RHS );
						
					}
					
					macrosHM.put( LHS, RHS );
					macroCount++;
				} else {
					if ( file == null ) {
						System.err.println( "\nerror: invalid format for macro from MACRO block, line <" + lineNumberIndex + "> line <" + line + ">");
					} else {
						System.err.println( "\nerror: invalid format for macro from file <" + file + "> line <" + lineNumberIndex + "> line <" + line + ">");
					}
					
					bug = true;
				}
									
			}
			
			if ( bug ) {
				System.exit( 17 );
			}
			
			if ( macroCount == 0 ) {
				System.err.println( "\nfatal: a macro file was given, but no macros were found in it");
				System.exit( 18 );
			//}
			}
		} catch (final FileNotFoundException e) {
			System.err.println( "\nfatal: can't find macro file from -m switch <" + file + ">.");
			System.exit( 18 );
		} catch (final IOException e) {
			System.err.println( "\nfatal: can't read macro file from -m switch <" + file + ">. " + e.getMessage());
			System.exit( 18 );
		} finally {
			try {
				if ( br != null ) {
					br.close();
				}
			} catch (final IOException e) {
				System.err.println( "\nfatal: There was an error closing macros file, cleaning up resources." );
				System.exit( 13 );
			}
		}
		
	}
	
	/*
	 * called line by line from the parsing of MACROS block
	 */
	
	static void addSingleMacro( String line, short lineNumber, Options opt ) {
		bug = false;
		macroCount = 0;
		String tmpMacroValue = null;
		
		/* it must be a macro line
		 * format (\s*)(macro name)\s*=(macro regex) 
		 */
		if ( line == null ) {
			System.err.println( "\nfatal: a macro was declared on line <" + lineNumber + ">, but the macro value was null");
			System.exit( 18 );
		}
				
		matcherMacroInput = patternMacroInput.matcher( line );
		
		if  ( matcherMacroInput == null)  {
			System.err.println("\nfatal: null matcherMacroInput in MACROS, line is <" + line + ">");
			System.exit( 18 );
		}
				
		if ( matcherMacroInput.find() ) {
			// matched lets save it
			// but first need to make it in STRING format
			/*
			 * this is macros block so see if we are over riding and need a warning
			 */
			if ( opt.getWarnMacroOverRide() ) {
				// they want a warning so we need to pre-check
				if ( (tmpMacroValue = macrosHM.get( matcherMacroInput.group( 1 )))  != null ) {
					System.out.println( "\nwarning: macro <" + matcherMacroInput.group( 1 ) + "> value of <" + tmpMacroValue + "> is now over-written with <" + matcherMacroSubstitution.group( 2 ) + "> from line <" + lineNumber + ">." );
				}
			}
			
			/*
			 * need to insert code here in case the definition includes other macros
			 * 
			 */
							
			// see if the RightHandSide (RHS) has macros to be expanded e.g. ADDR <CITY>,<STATE> <ZIP>
			String RHS = matcherMacroInput.group( 2 );
			final String LHS = matcherMacroInput.group( 1 );
								
			matcherMacroSubstitution = patternMacroSubstitution.matcher( RHS );		// reusing a previous matcherMacroInput!			
			
			while ( matcherMacroSubstitution.find() ) {
				// loop since could be more than one
				// make sure key is defined
				String value;
				
				if ( (value = macrosHM.get( matcherMacroSubstitution.group( 1 )) ) != null  ) {
					RHS = RHS.replace( matcherMacroSubstitution.group( 0 ) , value);
				} else {
					System.err.println( "\nfatal: a macro definition included other macros which were not defined in <" + file + "> line <" + lineNumber + ">.");
					System.exit( 18 );
				}
				
				matcherMacroSubstitution.reset( RHS );
				
			}
			
			macrosHM.put( LHS, RHS );
			macroCount++;
						
		} else {
			if ( file == null ) {
				System.err.println( "\nerror: invalid format for macro from MACRO block, line <" + lineNumber + "> line <" + line + ">");
			} else {
				System.err.println( "\nerror: invalid format for macro from file <" + file + "> line <" + lineNumber + "> line <" + line + ">");
			}
			
			bug = true;
		}
			
		if ( bug ) {
			System.exit( 17 );
		}
		
		if ( macroCount == 0 ) {
			System.err.println( "\nfatal: a macro file was given, but no macros were found in it");
			System.exit( 18 );
		}
		
		return;
	}

	/*
	 * showMacros mostly to help a confused used who has over written a file maro with a macro block
	 * or has a macro of macros
	 */
	static void showMacros() {
		// show final macros from file + block and after any macro expansions
		System.out.println("Below is the superset of macros available based on the macro file and/or\nmacros block in the -c <file>.\n");
		final Set<Map.Entry<String, String>> set = macrosHM.entrySet();
		for (final Map.Entry<String, String> me : set) { System.out.println(me.getKey()+ "\n" + me.getValue() + "\n"); }
	}		
	
} // end class
