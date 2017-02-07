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
 * Validate - a console tool to validate the contents of "data" files that might be used to configure, install
 * or run an application. Validate will read a "config" file created by an ascii text editor. The config file in conjuction
 * with the command line options will determine how to perform the file validation. A sample config file will be written to STOUT with the "-p configFile" switch.
 * 
 * The other required file(s) is provided via the "-f" option; this is the file(s) to be validated
 * by the regex(es) in the RULES block of the config file.
 * 
 * A full list of options is available with the "-h" or "-?" switch.
 *
 * See the manpage for full details and examples.
 * 
 *
 * @author Bill Lanahan
 *
 */

package validate;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

public class Validate {

	static boolean testMode = false;
	static boolean regexTimedTestMode = false;
	static boolean helpMode = false;
	static boolean regexTestMode = false;
	static boolean dirFlag = false;
	static boolean fileIsCmdLine = false;
	static boolean haveConfigFile = false;
	static boolean printMode = false;
	static boolean showMacros = false;
	static boolean valueMode = false;
	
	// don't move, these must precede following final Strings
	static final String blkStart = System.getProperty( "blkStart", "^\\s*%%" );
	static final String blkEnd = System.getProperty( "blkEnd", "^\\s*%%$" );
	static final Pattern blkEndPattern = Pattern.compile( blkEnd );

	public static enum format { JAVA, NAME_VALUE, CUSTOM, DELIMITED, LINE, VALUE_LINE, VALUE_DELIMITED };

	static String fileNameInput = null;
	static String fileNameConfig = null;
	static String fileNameRegex = null;
	static String dirPath = null;
	static String macrosFile = null;
	static String ruleId = null;
	static String valueString = null;

	static Map<String, Rule> ruleMap = null;
	static ArrayList<String> ruleList = null;
	static File dirPathObj = null;
	static int returnCode = 0;
	static int total_fails = 0;
	static boolean regexMultiLine = false;
	static boolean printTCcomment = false;
	static String regexCommentString = null;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		ParseConfig pcf = null;
		// read command line arguments
		short argsCount = 0;
		
		// see if blkStart or end were given and MAY cause problems
		if ( (blkStart.charAt(0) != '^') || (blkEnd.charAt(0) != '^') ) {
			System.err.println( "\n\nfatal: the regex given by blkStart and blkEnd MUST have the start of line anchor '^'." );
			System.exit( 10 );
		}
		
		if( blkEnd.matches("\\s+") ) {
			System.out.println( "\n\nfatal: blkEnd can not contain white space characters." );
			System.exit( 10 );
		}

		if( blkStart.matches("\\s+") ) {
			System.out.println( "\n\nfatal: blkStart can not contain white space characters." );
			System.exit( 10 );
		}
		
		if ( args.length == 0 ) {
			usage();
		}

		while ( argsCount < args.length ) {

			if ( args[argsCount].equals("-h") ) {
				Validate.helpMode = true;
				argsCount++;

			}	else if ( args[argsCount].equals("-t") ) {
				Validate.testMode = true;
				argsCount++;
			
			} else if ( args[argsCount].equals("-m") ) {
				argsCount++;

				if ( argsCount == args.length ) {
					System.err.println( "\nerror: -m switch requires an argument.\n" );
					usage();
				} else {
					macrosFile = args[argsCount];
					argsCount++;
				}				 

			} else if ( args[argsCount].equals("-?") ) {
				Validate.helpMode = true;
				argsCount++;

			} else if ( args[argsCount].equals("-M") ) {
				Validate.regexMultiLine = true;
				argsCount++;
				
			} else if ( args[argsCount].equals("-P") ) {
				Validate.printTCcomment = true;
				argsCount++;

			} else if ( args[argsCount].equals("-p") ) {
				Validate.printMode = true;
				argsCount++;

				if ( argsCount == args.length ) {
					System.err.println( "\nerror: -p switch requires an argument." );
					usage();
				}

				if ( args[argsCount].equals( "macroFile" ) ) {
					Validate.printMacroFile();
				} else if ( args[argsCount].equals( "macros" ) ) {
					// this will print and exit later after macros are processed in the parseConfig
					showMacros = true;
				} else if ( args[argsCount].equals( "configFile" ) ) {
					Validate.printConfigFile();
				} else if ( args[argsCount].equals( "regex" ) ) {
					Validate.printRegex();
				} else {
					System.err.println( "\nerror: invalid argument to -p switch.\n" );
					usage();
				}

				argsCount++;				 

			} else if ( args[argsCount].equals("-f") ) {
				Validate.fileIsCmdLine = true;
				argsCount++;

				if ( argsCount == args.length ) {
					System.err.println( "\nerror: -f switch requires an argument.\n" );
					usage();
				} else {
					Validate.fileNameInput = args[argsCount];
					argsCount++; 
				}


			} else if ( args[argsCount].equals( "-c" ) ) {
				argsCount++;

				if ( argsCount == args.length ) {
					System.err.println( "\nerror: -c switch requires an argument.\n" );
					usage();
				} else {
					fileNameConfig = args[argsCount];
					haveConfigFile = true;
					argsCount++;
				}

			} else if ( args[argsCount].equals( "-i" ) ) {
				argsCount++;

				if ( argsCount == args.length ) {
					System.err.println( "\nerror: -i switch requires an argument.\n" );
					usage();
				} else {
					ruleId = args[argsCount];
					argsCount++;
				}

			} else if ( args[argsCount].equals( "-R" ) ) {
				// for the REGEX test file
				regexTestMode = true;
				argsCount++;

				if ( argsCount == args.length ) { 
					System.err.println( "\nerror: -R switch requires an argument.\n" );
					usage();
				} else {
					Validate.fileNameRegex  = args[argsCount];
					argsCount++;
				}
				
			} else if ( args[argsCount].equals( "-T" ) ) {
				// for the REGEX test file
				regexTimedTestMode = true;
				argsCount++;

				if ( argsCount == args.length ) { 
					System.err.println( "\nerror: -T switch requires an argument.\n" );
					usage();
				} else {
					Validate.fileNameRegex  = args[argsCount];
					argsCount++;
				}

			} else if ( args[argsCount].equals( "-C" ) ) {
				// for the REGEX test file, for the CommentString
				argsCount++;

				if ( argsCount == args.length ) {
					System.err.println( "\nerror: -C switch requires an argument.\n" );
					usage();
				} else {
					regexCommentString  = args[argsCount];
					argsCount++;
				}
							
			} else if ( args[argsCount].equals( "-d" ) ) {
				argsCount++;

				if ( argsCount == args.length ) {
					System.err.println( "\nerror: -d switch requires an argument." );
					System.exit( 10 );
				} else {
					Validate.dirPath = args[argsCount];
					Validate.dirFlag = true; 

					dirPathObj = new File( dirPath );

					if ( ! dirPathObj.isDirectory() ) {
						System.err.println( "\nerror: -d switch <" + dirPath + "> is not a valid directory.");
						System.exit( 10 ); 
					} else {
						dirPathObj = null; // no longer needed now that its checked
					}

					argsCount++;
				}

			} else if ( args[argsCount].equals( "-v" ) ) {
				argsCount++;

				if ( argsCount == args.length ) {
					System.err.println( "\nerror: -v switch requires an argument." );
					System.exit( 10 );
				} else { 
					Validate.valueString = args[argsCount];
					Validate.valueMode = true; 
					argsCount++;
				} 
				
			} else {
				System.err.println( "\nerror: invalid command line option <" + args[argsCount] + ">.");
				System.exit( 10 );
			}
		}   // end of options reading


		// check the options
		if ( Validate.helpMode ) {
			usage();
		}

		if ( ! Validate.fileIsCmdLine && Validate.dirFlag ) {
			System.err.println( "\nerror, -d switch is only (optionally) used with -f switch.\n" );
			usage();
		}
		
		if ( Validate.fileIsCmdLine && (ruleId == null) ) {
			System.err.println( "\nerror, -f switch also requires -i switch.\n" );
			usage();
		}
		
		if ( ! (Validate.fileIsCmdLine || Validate.valueMode) && (ruleId != null) ) {
			System.err.println( "\nerror, -i switch also requires -f|-v switch.\n" );
			usage();
		}
	
		// must at least have a config file even if it has no macros block
		if ( Validate.showMacros ) {
			if ( ! Validate.haveConfigFile ) {
				usage();
			}
		}
		
		if ( regexTestMode && regexTimedTestMode ) {
			System.err.println( "\nerror, -R and -T are mutually exclusive\n" );
			usage();
		}

		if ( Validate.valueMode && (Validate.regexTestMode || Validate.regexTimedTestMode || fileIsCmdLine )) {
			System.err.println( "\nerror: -v is mutually exclusive with -R|-T|-f");
			usage();
		} 
		
		if ( Validate.valueMode && ruleId == null ) {
			System.err.println( "\nerror: -v switch requires the -i id switch");
			usage();
		} 
		
		// only 4 major choices it testing a regex or its going to use the configFile
		if ( Validate.regexTestMode ) {
			Regex regex = new Regex( fileNameRegex, regexCommentString, regexMultiLine, printTCcomment );
			regex.readFile();
			System.exit( 0 );
		} else if ( regexTimedTestMode ) {	
			RegexTimed regex = new RegexTimed( fileNameRegex, regexCommentString, regexMultiLine );
			regex.readFile();
			System.exit( 0 );
		} else {
			// here we begin the file validate part of the main tool
			
			// assuming all options are correct
			// read the configFile and then execute depending on the test flag
			pcf = new ParseConfig( fileNameConfig );
			assert pcf == null : "\nfatal: ParseConfigFile object could not be created.\n";
			pcf.parse();
					
			
			// at this point we have all the RULES parsed and saved
			if ( testMode ) {
				System.out.println( "\ninfo: validate has completed static testing of the config file with no errors.");
				System.exit( returnCode );
			} else {
				Rule rule = null;
				
				// we need to iterate through the list of rules and test them
				// if we had cmd line files we only do that one rule
				if ( fileIsCmdLine && (ruleId != null) ) {
					if ( ruleMap == null || ! ruleMap.containsKey( ruleId )) {
						System.err.println( "\nerror: the -i id  <" + ruleId + "> is not an existing id in the configuration file.");
						System.exit( 10 );
					}
					
					rule = ruleMap.get( ruleId );
					final String array[] = fileNameInput.split(",");	
					for (int i = 0; i < array.length; i++ ) {
						rule.fileAdd( array[i] ); // now cmd line files are stored in the rule object
					}
					
					rule.checkIt();
					// were done
					System.exit( returnCode );
					
				} else if ( Validate.valueMode ) {
					// not command line so we will just do every Rule in the ruleMap
					// we want these in order of config file so take from ruleList
																			
					// only one rule id to do
					if ( ruleMap.containsKey( Validate.ruleId ) ) {
						ruleMap.get( Validate.ruleId).checkIt( );
						System.exit( returnCode );
					} else {
						System.err.println( "\nerror: -i id <" + Validate.ruleId + "> does not exist in config file.");
						System.exit( 99 );
					}
									
				} else {
					// not command line so we will just do every Rule in the ruleMap
					// we want these in order of config file so take from ruleList
							
					String ruleID = null;
									
					int size = ruleList.size();
					int position = 0;
					
					while ( size > 0 && position < size ) {
						ruleID = ruleList.get( position++ ); 
						
						if ( ruleID == null ) {
							System.err.println("\nfatal: a stored ruleId is null, program error in validate.java");
							System.exit( 99 );
						}
						
						ruleMap.get( ruleID ).checkIt();
					}
					
				    // were done, just report total fails for the entire run
					if ( total_fails > 0 ) {
						System.out.println( "\n% TOTAL FAILS: " + total_fails );
					} else {
						// the 0 case we add a trailing SPACE to a shell can easily know if the QTY is more than zero
						System.out.println( "\n% TOTAL FAILS: " + total_fails + " " );
					}
					
				    System.exit( returnCode );
				}
				
			} // end of NON-testMode, which means execution of Rule(s)

		} // end of -c processing

	}

	static void usage() {
		System.out.println( "\n\n" +
				"usage: validate ... \n\n" +
				"       validate -c file [-f file...|-v valueString] -i id [-m macroFile] [-d dirPath] [-t]\n" +
				"       validate -c file [-m macroFile]\n" +
				"       validate -c file [-m macroFile] -t\n" +
				"       validate -h | -?\n" +
				"       validate -R regexTestFile [-C \"commentString\"] [-M] [-P]\n" +
				"       validate -T regexTestFile [-C \"commentString\"] [-M]\n" +
				"       validate -p macroFile|configFile\n" +
				"       validate -p macros -c configFile [-m macroFile]\n" +
				"\n\n" +
				"       where\n" +
				"         -c file, configuration file specifies OPTIONS, MACROS, RULES, and REGEX\n" +
				"         -d dirPathString, prepended to data file (-f) on cmd line or in RULES\n" +
				"         -h | -? this help usage\n" +
				"         -i id, to choose the appropriate RULE block\n" +
				"         -f file, multiple files if comma separated\n" +
				"         -m macro, file of macros to use in Regexes\n" +
				"         -p macros, prints combined set of macros\n" +
				"         -p macroFile, prints the format of a macroFile and some comments\n" +
				"         -p configFile, prints the format of a configFile and some comments\n" +
				"         -p regex, prints the Java supported regular expressions\n" +
				"         -t test mode, checks config file/macros, but no data file validation\n" +
				"         -v valueString, a string to be checked by the given rule id\n" +
				"         -C commentRegex, regex to denote a comment line in a regex file\n" +
				"         -M allow regex in -R or -T to be multi line with \\ continuation\n" +
				"         -P print ONLY comments in -R that follow the regex line\n" +
				"         -R regexFile, test file of a regex and test cases\n" +
				"         -T regexFile, test file with look at execution speed\n" +
				"\n\n" );

		System.exit( 0 );
	}

	static void printMacroFile() {
		System.out.println( "\n" +
				"# Macros as specified by a file with the -m or -M switch or\n" +
				"# is within the %% MACROS block of a config file, is shown below the \"## cut here ##\" line.\n" +
				"# Blank lines and comment lines (those that begin with optional whitespace,\n#	are supported.\n" +
				"\n# Format is: <opt. whitespace><macro name><opt. whitespace>=<value>\n" +
				"# A macro name is one or more of \"word\" characters\n" +
				"# A macro value is a regex, read from the equals after the macro name until the end of the line\n" +
				"# Any quotes become part of the macro.\n" +
				"\n\n## cut here ##\n" +
				"# macro file for project XYZ installation\n\n" +
				"ZIP_CODE=\\d{5}\n" +
				"firstName=[A-Z]{1}[a-z]+\n" +
				"lastName5char=[A-Z][a-z]{4}\n" +
				"\n" );


		System.exit( 0 );
	}

	static void printConfigFile() {
		
		System.out.println( "\n\n" +
				"# A sample config file with default value of options\n\n\n" +
				"%% OPTIONS\n" +
				"    # the block title is case insensitive\n" +
				"    # option names are case insensitive\n" +
				"    # this section is optional, if it exists it must be the first block\n" +
				"    # ...String values may optionally be quoted.\n" +
				"    # ...Regex values must NOT have quotes, unless they are to be matched\n" +
				"    # the value \"null\" is never entered, terminate after the '=' for any null\n" +
				"    # string or regex\n" +
				"    #\n\n Note: below the options are all commented out becasue many have end-of-line\n" +
				"    # comments, or mutliple values shown as a reference - both of these would fail at run time as they are\n" +
				"    # The value listed ARE the current default values.\n" +
				"    # Note the options are shown commented out since many are shown with multiple values\n" + 
			    "    # or end-of-line comments which would cause an error.\n\n" +
				"#linePrefixRegex =\n" +
				"#lineSuffixRegex =\n" +
				"#lineReplaceRegex =\n" +
				"#lineReplaceDelimiterRegex =/\n\n" +
				"#delimiterRegex == (regex is one =)\n" +
				"#dirPathString =\n" +
				"#EOLdelimiter =false\n" +
				"#errorReportDetails =empty|numberLine|line|all (first letter works)\n" + 		
				"#errorReportSummary =always|yes|failure (first letter works)\n" + 		
				"#errorFieldUnderline =true\n\n" +
				"#extraFieldCount =0\n\n" +
				"#fileLineContinuation =false\n\n" +
				"#lineCheckRegex =\n" +
				"#lineSkipRegex =^\\s*#|^\\s*$ \n" +
				"#lineShow =false\n\n" +
				"#macroContinuation =true\n" +
				"#macroEndString =\">\"\n" +
				"#macroStartString =\"<\"\n\n" +
				"#regexCommentRegex =^\\s*#\n" +
				"#regexEndBlockRegex =^\\s*%%\n" +
				"#regexLineContinuation =false\n\n" +
				"#showValidData =false\n" +
				"#showToolTitle =true\n\n" +
				"#useMacros =false\n" +
				"#useLineRangeRestrictions =true\n" +
				"#validLineUnderline =false\n\n" +
				"#warnDuplicates =true\n" +
				"#warnExtraFields =false\n" +
				"#warnMacroOverRide =false\n" +
				"#warnRuleWithoutFile =true\n" +
				"#warnUncheckedName =false\n" +
				"#warnRegexUnused =true\n" +
				"#warnInvalidOption =true\n" +
				"%%\n\n" +
				"%% MACROS\n" +
				"     # spaces can precede or follow the macro name\n" +
				"     # if they follow the '=' they are part of the macro\n" +
				"macroName1=macro_value_or_regex1\n" +
				"macroName2=macro_value_or_regex2\n" +
				"%%\n" +
				"\n# One or more RULE blocks follow\n\n" +
				"%% RULE my_first_rule\n" +
				"    file=my_file\n" +
				"    format=NameValue\n" +
				"    # other options to supplement or\n" +
				"    # override global options\n" +
				"%% REGEX\n" +
				"    # regular expressions per format\n" +
				"    # selected i.e.\n" +
				"zip_code=^\\d{5}$\n" +
				"%%\n"
				);
			
		System.exit( 0 );
	}

	static void printRegex() {
		System.out.println( "\n" +
		"Some explanation of regular expression syntax (as of Java 6). Users of the validate tool\n" +
		"will likely use a small subset of these on a normal basis.\n" +
		"\n" +
		"Characters \n" +
		"==========\n" +
		"x         The character x \n" +
		"\\         The backslash character \n" +
		"\\0n       The character with octal value 0n (0 <= n <= 7) \n" +
		"\\0nn      The character with octal value 0nn (0 <= n <= 7) \n" +
		"\\0mnn     The character with octal value 0mnn (0 <= m <= 3, 0 <= n <= 7) \n" +
		"\\xhh      The character with hexadecimal value 0xhh \n" +
		"\\uhhhh    The character with hexadecimal value 0xhhhh \n" +
		"\\t        The tab character ('\\u0009') \n" +
		"\\n        The newline (line feed) character ('\\u000A') \n" +
		"\\r        The carriage-return character ('\\u000D') \n" +
		"\\f        The form-feed character ('\\u000C') \n" +
		"\\a        The alert (bell) character ('\\u0007') \n" +
		"\\e        The escape character ('\\u001B') \n" +
		"\\cx       The control character corresponding to x \n" +
		"\n"  +		
		"Character classes \n" +
		"=================\n" +
		"[abc]          a, b, or c (simple class) \n" +
		"[^abc]         Any character except a, b, or c (negation) \n" +
		"[a-zA-Z]       a through z or A through Z, inclusive (range) \n" +
		"[a-d[m-p]]     a through d, or m through p: [a-dm-p] (union) \n" +
		"[a-z&&[def]]   d, e, or f (intersection) \n" +
		"[a-z&&[^bc]]   a through z, except for b and c: [ad-z] (subtraction) \n" +
		"[a-z&&[^m-p]]  a through z, and not m through p: [a-lq-z](subtraction) \n" +
		"\n" +
		
		"Predefined character classes \n" +
		"============================\n" +
		".      Any character (may or may not match line terminators) \n" +
		"\\d     A digit: [0-9] \n" +
		"\\D     A non-digit: [^0-9] \n" +
		"\\s     A whitespace character: [ \\t\\n\\x0B\\f\\r] \n" +
		"\\S     A non-whitespace character: [^\\s] \n" +
		"\\w     A word character: [a-zA-Z_0-9] \n" +
		"\\W     A non-word character: [^\\w] \n" +
		"  \n" +
		"POSIX character classes (US-ASCII only) \n" +
		"=======================================\n" +
		"\\p{Lower}    A lower-case alphabetic character: [a-z] \n" +
		"\\p{Upper}    An upper-case alphabetic character:[A-Z] \n" +
		"\\p{ASCII}    All ASCII:[\\x00-\\x7F] \n" +
		"\\p{Alpha}    An alphabetic character:[\\p{Lower}\\p{Upper}] \n" +
		"\\p{Digit}    A decimal digit: [0-9] \n" +
		"\\p{Alnum}    An alphanumeric character:[\\p{Alpha}\\p{Digit}] \n" +
		"\\p{Punct}    Punctuation: One of !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ \n" +
		"\\p{Graph}    A visible character: [\\p{Alnum}\\p{Punct}] \n" +
		"\\p{Print}    A printable character: [\\p{Graph}\\x20] \n" +
		"\\p{Blank}    A space or a tab: [ \\t] \n" +
		"\\p{Cntrl}    A control character: [\\x00-\\x1F\\x7F] \n" +
		"\\p{XDigit}   A hexadecimal digit: [0-9a-fA-F] \n" +
		"\\p{Space}    A whitespace character: [ \\t\\n\\x0B\\f\\r] \n" +
		"\n" +
		"java.lang.Character classes (simple java character type) \n" +
		"========================================================\n" +
		"\\p{javaLowerCase}   Equivalent to java.lang.Character.isLowerCase() \n" +
		"\\p{javaUpperCase}   Equivalent to java.lang.Character.isUpperCase() \n" +
		"\\p{javaWhitespace}  Equivalent to java.lang.Character.isWhitespace() \n" +
		"\\p{javaMirrored}    Equivalent to java.lang.Character.isMirrored() \n" +
		"\n" +
		"Boundary matchers \n" +
		"=================\n" +
		"^       The beginning of a line \n" +
		"$       The end of a line \n" +
		"\\b      A word boundary \n" +
		"\\B      A non-word boundary \n" +
		"\\A      The beginning of the input \n" +
		"\\G      The end of the previous match \n" +
		"\\Z      The end of the input but for the final terminator, if any \n" +
		"\\z      The end of the input \n" +
		"\n" +
		"Greedy quantifiers \n" +
		"==================\n" +
		"X?      X, once or not at all \n" +
		"X*      X, zero or more times \n" +
		"X+      X, one or more times \n" +
		"X{n}    X, exactly n times \n" +
		"X{n,}   X, at least n times \n" +
		"X{n,m}  X, at least n but not more than m times \n" +
		"  \n" +
		"Reluctant quantifiers \n" +
		"=====================\n" +
		"X??     X, once or not at all \n" +
		"X*?     X, zero or more times \n" +
		"X+?     X, one or more times \n" +
		"X{n}?   X, exactly n times \n" +
		"X{n,}?  X, at least n times \n" +
		"X{n,m}? X, at least n but not more than m times \n" +
		"\n" +
		"Possessive quantifiers \n" +
		"======================\n" +
		"X?+     X, once or not at all \n" +
		"X*+     X, zero or more times \n" +
		"X++     X, one or more times \n" +
		"X{n}+   X, exactly n times \n" +
		"X{n,}+  X, at least n times \n" +
		"X{n,m}+ X, at least n but not more than m times \n" +
		"\n" +
		"Logical operators \n" +
		"=================\n" +
		"XY      X followed by Y \n" +
		"X|Y     Either X or Y \n" +
		"(X)     X, as a capturing group \n" +
		"  \n" +
		"Back references \n" +
		"===============\n" +
		"\\n      Whatever the nth capturing group matched \n" +
		"\n" +
		"Quotation \n" +
		"=========\n" +
		"\\       Nothing, but quotes the following character \n" +
		"\\Q      Nothing, but quotes all characters until \\E \n" +
		"\\E      Nothing, but ends quoting started by \\Q \n" +
		"\n" +
		"Special constructs (non-capturing) \n" +
		"==================================\n" +
		"(?:X)              X, as a non-capturing group \n" +
		"(?idmsux-idmsux)   Nothing, but turns match flags i d m s u x on - off \n" +
		"(?idmsux-idmsux:X) X, as a non-capturing group with the given flags on - off\n" +
		"                   flags: i=case insensitive; d=unix mode; m=multiline mode\n" +
		"                   s=dotall mode \".\" will match line terminator\n" +
		"                   u=unicode aware folding; x=allow whitespace and EOL comments\n" +
		"                   in pattern\n" +
		"(?=X)              X, via zero-width positive lookahead \n" +
		"(?!X)              X, via zero-width negative lookahead \n" +
		"(?<=X)             X, via zero-width positive lookbehind \n" +
		"(?<!X)             X, via zero-width negative lookbehind \n" +
		"(?>X)              X, as an independent, non-capturing group \n" +
		"\n" +
		"Notes\n" +
		"=====\n" +
		"Backslashes, escapes, and quoting:\n" +
		"The backslash character ('\\') serves to introduce escaped constructs, as \n" +
		"defined in the table above, as well as to quote characters that otherwise \n" +
		"would be interpreted as unescaped constructs. Thus the expression \\\\ matches \n" +
		"a single backslash and \\{ matches a left brace. \n" +
		"\n" +
		"It is an error to use a backslash prior to any alphabetic character that does \n" +
		"not denote an escaped construct; these are reserved for future extensions to \n" +
		"the regular-expression language. \n\nA backslash may be used prior to a non-alphabetic character regardless of \n" +
		"whether that character is part of an unescaped construct. \n" +
		"\n" +
		"(For Java developers)\n" +
		"Backslashes within string literals in Java source code are interpreted as \n" +
		"required by the Java Language Specification as either Unicode escapes or other\n" +
		"character escapes. It is therefore necessary to double backslashes in string \n" +
		"literals that represent regular expressions to protect them from interpretation\n" +
		"by the Java bytecode compiler. The string literal \"\\b\", for example, matches\n" +
		"a single backspace character when interpreted as a regular expression, \n" +
		"while \"\\\\b\" matches a word boundary. The string literal \"\\(hello\\)\" is illegal \n" +
		"and leads to a compile-time error; in order to match the string (hello) the \n" +
		"string literal \"\\\\(hello\\\\)\" must be used. \n" +
		"\n" );

		System.exit( 0 );
	}

	// available to any object to update
	static void rc( int rc ) {
		if ( rc > returnCode ) {
			returnCode = rc;
		}
	}
	
	// for simple file status on exit
	static void saveTotalFails ( int fails ) {
		if ( fails >= 0 ) {
			total_fails += fails;
		}
	}


}
