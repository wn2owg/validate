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

import validate.Validate.format;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

/*
 * Rule will be an instance for each RULE with an ID in the config file. There must be at least 1 for a working program.
 * 
 * @author Bill Lanahan
 */

class Rule {

	private final ArrayList<String> fileList = new ArrayList<String>();
	private ArrayList<String> nvRegexUsed = new ArrayList<String>();
	private Options ruleOption = null;
	private int errCount;
	private int lineNumber = 0;
	private File inputFile = null;
	private BufferedReader br = null;
	private String dir = null;
	private String f = null;
	private String ruleID = null;
	private String line = null;
	private String tmpline = null;
	private String configFileName = null;
	private String lineCheckRegex = null;
	private String lineSkipRegex = null;
	private boolean continuationState = false;
	boolean lineSkipRegexExists = false;
	boolean lineCheckRegexExists = false;
	
	// for genericLineEdits
	StringBuilder linePrefixSuffixRegex = new StringBuilder ( 50 );
	String linePrefixRegex = null;
	String lineSuffixRegex = null;
	String lineReplaceRegex = null;
	boolean linePrefixRegexExists = false;
	boolean lineSuffixRegexExists = false;
	Pattern linePrefixSuffixPattern = null;
	boolean lineReplaceFlag = false;
	String[] replaceArray = null;
	String replaceRegex = null;
	Matcher m = null;
		
	ArrayList<Pattern> lineFormatRegexList = new ArrayList<Pattern>(); // used with LINE format
	ArrayList<Integer> fromLineList = new ArrayList<Integer>(); // used with LINE format
	ArrayList<Integer> toLineList = new ArrayList<Integer>(); // used with LINE format
	private Pattern lineSkipPattern = null;
	private Pattern lineCheckPattern = null;
	private Matcher lineSkipMatcher = null;
	
	Object[] patternStringListArray; // used in delimited format
	Pattern fullDelimitedLinePattern;
	Matcher lineCheckMatcher = null;
	boolean hadFirstError = false;
	format ruleFormat  = null;
	HashMap<String, NVP> nvpHM = null ; // gets defined in RegexBlock
	NVP nvp = null;
	// next 3 NOT private used by RegexBlock
	StringBuilder outDups = new StringBuilder(100);
	StringBuilder outInfo = null;
	StringBuilder outUnchecked = null;
	Object[] patternListArray = null;
		
	Rule () {
		// default not used
	}
	
	Rule ( String ruleID, Options ruleOption, String f ) {
		this.ruleID = ruleID;
		this.ruleOption = ruleOption;
		this.configFileName = f; // need in summary output "f" is the data/user file
	}
	
	// setter/getter 	
		
	// the format will determine how the files to get validated are parsed
	public void setFormat( format value ) {
		this.ruleFormat = value;
	}
	
	public format getFormat( ) {
		return ruleFormat;
	}
	
	public String getRuleID( ) {
		return ruleID;
	}

	
	public void fileAdd( String ruleValue ) {
		fileList.add( new String(ruleValue ) );
	}
	
	// this actually does the validation
	void checkIt ( ) {
		
		switch ( ruleFormat ) {
			case NAME_VALUE:
				doNameValue();
				break;
			case JAVA:
				doJava();
				break;
			case DELIMITED:
				doDelimited();
				break;
			case CUSTOM:
				doCustom();
				break;
			case LINE:
				doLine();
				break;
			case VALUE_LINE: // similar to lines but doesn't read from a file
				doValueLine();
				break;
			case VALUE_DELIMITED: // similar to DELIMITED but doesn't read from a file
				doValueDelimited();
				break;
			default:
				System.err.println( "\nfatal: invalid <format=value> found in rule class.");
				System.exit ( 15 );
		}
	}

	/*
	 * a delimited file gets a regex for each column in the data
	 * after any COMMENT lines are skipped over
	 */
	private void doDelimited() {

		lineSkipRegexExists = false;
		lineCheckRegexExists = false;
		boolean errorFieldUnderline = ruleOption.getErrorFieldUnderline();
		char[] errorUnderline = null;
		char[] temp = null;
		String 	lineCheckRegex = null;
		lineCheckPattern = null;
		m = null;

		if ( (lineSkipRegex = ruleOption.getLineSkipRegex()) != null ) {
			lineSkipRegexExists = true;
		}

		if ( (lineCheckRegex = ruleOption.getLineCheckRegex()) != null ) {
			lineCheckRegexExists = true;
			lineCheckPattern = Pattern.compile( lineCheckRegex ); // already tested in options
		}

		
		// 	do the rest for EVERY file given		
		for (final String fileName : fileList) {
			errCount = 0;
			
			if ( fileName == null ) {
				if ( ruleOption.getWarnRuleWithoutFile() ) {
					System.err.println( "\nwarning: RULE with id <" + ruleID + "> has no <file=> specified; RULE is skipped.");
				}
				continue; // nothing else we can do with no file
			}

			if ( (dir = ruleOption.getDirPathString()) != null ) {
				f = dir + fileName;  
			} else {
				f = fileName;
			}

			lineNumber = 0;

			try {

				int expectedFieldCount = patternListArray.length; // number or Regexes in block
				// set size appropriately, but if extra fields are found java will grow size
				int ArrayListInitialSize = expectedFieldCount + 1 +  ruleOption.getExtraFieldCount();
				int actualFieldCount;
				boolean hadError = false;
				final StringBuilder delimiterRegexInParens = new StringBuilder();
				delimiterRegexInParens.append( "(" ).append( ruleOption.getDelimiterRegex() ).append( ")" );
				final Pattern delimiterPattern = Pattern.compile( delimiterRegexInParens.toString() );
				ArrayList<Integer> delimiterStart = new ArrayList<Integer>( ArrayListInitialSize );
				ArrayList<Integer> delimiterEnd = new ArrayList<Integer>( ArrayListInitialSize );
				int delimiterIndex = 0;
				int lineLength = 0;

				StringBuilder fields;
				String[] lineArray = null;
				lineSkipPattern = Pattern.compile( lineSkipRegex );
				Matcher delimiterMatcher = null;
				inputFile = new File(f);
				br = new BufferedReader(new FileReader(inputFile));
				outInfo = new StringBuilder( 500 );
				errCount = 0;
				lineNumber = 0;
				String EOLdelimiterAtEOL = ruleOption.getDelimiterRegex() + '$'; 
				Pattern EOLPat = Pattern.compile( EOLdelimiterAtEOL ); // will use on every non-null (non-comment too) line of input

				// handle editing
				// gets all options for prefix, suffix, replace
				genericLineEdittingPrep();

				while ( true ) {		// read all the lines in the file to validate
					fields = new StringBuilder( 500 );

					hadError = false;
					actualFieldCount = 0;
					lineLength = 0;

					line = br.readLine();

					if ( line == null ) {
						// file is done lets close and then check status
						checkEOFstatus( outInfo.toString(), lineNumber, errCount, "", f); // sends the DATA file
						outInfo = new StringBuilder( 500 );
						break;
					}

					// see if we should skip lines
					if ( lineSkipRegexExists ) {
						lineSkipMatcher = lineSkipPattern.matcher( line );
						if ( lineSkipMatcher.find() ) {
							continue;
						}
					}

					/*
					 * use of genericLineEdditing to adjust line before validation
					 */

					if ( lineReplaceFlag ) {
						//line = line.replaceAll( replaceArray[0], replaceArray[1]);
						Pattern p = Pattern.compile( replaceArray[0] );
						Matcher mm = p.matcher( line );
						if ( mm.find() ) {
							line = mm.replaceAll( replaceRegex );
						}
					}

					/*
					 *  we can trim off the start and/or end of each line BEFORE we look at the remainder "nv" pair
					 */
					if ( linePrefixRegexExists || lineSuffixRegexExists ) {
						m = linePrefixSuffixPattern.matcher( line );
						line = m.replaceAll( "" );
					}

					/*
					 * OK but we could still have more than we are interested in so last global check
					 * determines if we really parse the line
					 */
					//  use it to decide skip it or check it
					if ( lineCheckRegexExists ) {

						lineCheckMatcher = lineCheckPattern.matcher( line );
						if (! lineCheckMatcher.find() ) {
							continue;
						}
					}
					// end of genericLineEditting

					//  use it to decide skip it or check it
					if ( lineCheckRegexExists ) {

						lineCheckMatcher = lineCheckPattern.matcher( line );
						if ( ! lineCheckMatcher.find() ) {
							continue;
						}
					}

					lineNumber++;

					// for debugging let user see what is being checked
					if ( (ruleOption.getLineShow() == true)) {
						outInfo.append("\nvalidating: <").append(lineNumber).append(">: ").append(line);
					}

					lineLength = line.length();
					delimiterMatcher = delimiterPattern.matcher( line );

					/*
					 * expectedFieldCount=the number or regexes we have, thus the number of fields=actualFieldCount we expect
					 * BUT
					 * we have multiple end of line delimiter situations e.g.
					 * expectedFieldCount=2 so we expect 2 fields, we could have
					 * ONE:TWO
					 * ONE:TWO:
					 * less than 1 : would be an error TOO FEW FIELDS
					 * more than 2 : would be an error TOO MANY
					 * if : = 1 EOLdelimiter=false OK
					 * if : = 2 EOLdelimiter=true OK
					 * if : = 1 or 2 AND EOLdelimiter=optional OK
					 */

					// lets see what the input line ends with

					Matcher EOLmatch = EOLPat.matcher( line );

					if ( EOLmatch.find() ) {
						// if here line ends in a delimiter
						if ( "false".equals(ruleOption.getEOLdelimiter() ) ) {
							// line is no good since no EOLdelimiter wanted and its there
							outInfo.append("\n").append(lineNumber).append("\n").append(line).append("\n  <error, final delimiter not expected>\n");
							errCount++;
							hadError = true;
							Validate.rc( 2 ); // record error
						}

						// if here: we had an EOLdelimiter lets trim it off - but the line might not be correct
						line = line.replaceAll( EOLdelimiterAtEOL, "");

					} else {
						// EOLdelimiter is NOT there: if optional its ok, but if required its error
						if ( "true".equals( ruleOption.getEOLdelimiter() ) ) {
							outInfo.append("\n").append(lineNumber).append(":\n").append(line).append("\n  <error, final delimiter missing>\n");
							errCount++;
							hadError = true;
							Validate.rc( 2 ); // record error
						}
					}

					EOLmatch = null;

					// since EOL is taken care of now we need to be concerned with actual fields of data
					// ***** so the rest below is ONLY needed if we have NOT had an error
					boolean fieldErr = false;

					if ( hadError == false ) {
						delimiterIndex = 0;

						if ( errorFieldUnderline ) {
							temp = new char[ lineLength ];
							Arrays.fill( temp,  '0' ); 
						}

						delimiterIndex = 0;
						while ( delimiterMatcher.find() ) { // get the start and end of each delimiter

							delimiterStart.add( delimiterMatcher.start() );
							delimiterEnd.add( delimiterMatcher.end() );

							if ( errorFieldUnderline ) {
								Arrays.fill( temp, delimiterStart.get(delimiterIndex), delimiterEnd.get(delimiterIndex), '1');
							}	

							delimiterIndex++;
						}

						lineArray = line.split( ruleOption.getDelimiterRegex(), delimiterIndex + 1);

						// from the delimiterArray we can build a str + start/end arrays needed only for underline
						int[] stringStart = null;
						int[] stringEnd = null;

						boolean done = false;
						actualFieldCount = lineArray.length;

						/*
						 * because we allow a trailing field in certain cases of EOL we need to 
						 * check more that actual and expected count
						 */

						if ( actualFieldCount < expectedFieldCount ) {
							// failure
							// add a test in case it was just whitespace

							// only in the "all case"
							if ( ruleOption.getErrorReportDetails() == 'a' ) {

								if ( line.matches("\\s*") ) {
									outInfo.append("\n\n").append(lineNumber).append(
											":  << error, input is just whitespace, therefore less fields than expected for the give regexes. Consider adding to lineSkipRegex. >>\n");
								} else {
									outInfo.append("\n\n").append(lineNumber).append(":\n").append(line).append("\n  <error, less fields than expected for the given regexes>\n");
								}

							}

							errCount++;
							hadError = true;
							Validate.rc( 2 ); // record error
							done = true;

						} else if ( actualFieldCount == expectedFieldCount ) {
							; // perfect just check fields
						} else {
							// so actual is greater
							// special case: the user could want extra end fields but not test them

							if ( ruleOption.getExtraFieldCount() >= 1 ) { 
								if ( actualFieldCount > expectedFieldCount + ruleOption.getExtraFieldCount() ) {
									// too many call it an error
									// only in the "all case"
									if ( ruleOption.getErrorReportDetails() == 'a' ) {
										outInfo.append("\n").append(lineNumber).append(":\n").append(line).append("\n  <error, <").append(
												actualFieldCount - expectedFieldCount).append("> extra field(s) exceed the count of <").append(
														ruleOption.getExtraFieldCount() ).append("> that was specified>");
									}

									errCount++;
									hadError = true;
									Validate.rc( 2 );

								} else {
									if (ruleOption.getWarnExtraFields() == true ) {
										outInfo.append("\n").append(lineNumber).append(":\n").append(line).append("\n  <warning, <").append(
												actualFieldCount - expectedFieldCount).append("> extra field(s), only the first <").append(
														expectedFieldCount).append("> are validated>");

									}
									Validate.rc( 1 );
								}


							} else if ( ruleOption.getExtraFieldCount() < 0 ) {
								; // just ignore them without warning
							} else {
								// by default extra == 0 so the user has an error to deal with

								// only in the "all case"
								if ( ruleOption.getErrorReportDetails() == 'a' ) {
									outInfo.append("\n").append(lineNumber).append(":\n").append(
											line).append("  <error, too many fields for the given regexes> ");
								}

								errCount++;
								hadError = true;
								Validate.rc( 2 ); // record error
								done = true;

							} 


						} // end of if for had delimiter

						// before we check if each field matched we want to know the position of strings IF
						// the user wants underlining else don't bother


						// we had the right delimiter state above so need to do fields
						if ( ! done ) {

							if ( errorFieldUnderline ) {
								errorUnderline = new char[ lineLength ];
								Arrays.fill( errorUnderline, ' ' ); // start clean and update to ^ as needed
								stringStart = new int[ lineArray.length ]; 
								stringEnd = new int[ lineArray.length ];
								int index = 0;
								boolean marking = false;
								int p = 0;


								for ( p = 0; p < lineLength; p++ ) {
									// walk the temp[] and set string start/end values
									if ( marking == false ) {
										if ( temp != null && temp[ p ] == '0' ) {
											marking = true;
											stringStart[ index ] = p;
										}
									} else {
										// marking = true
										if ( temp[ p ] == '1' ) {
											stringEnd[ index ] = p;
											marking = false;
											index++;
										}
									}
								}

								if ( marking == true ) {
									// need to close the stringEnd
									stringEnd[ index ] = p;
								}

							} // end if errorFieldUnderline

							// go field by field
							// we will still use the groups captured to determine string lengths
							int j = 0;
							for ( ; j < expectedFieldCount; j++ ) {

								boolean justNull = false;
								m = ((Pattern)patternListArray[ j ]).matcher( lineArray[ j ] ); // reset the matcher for the next pattern

								if ( ! m.find() ) { // NO-Match=error

									fieldErr = true;
									Validate.rc( 2 ); // record error

									if ( fields.length() > 0 ) {
										fields.append( "," );
									} else {
										fields.append( ":" );
									}

									// special case mark a null field that failed
									if ( lineArray[j].length() == 0 ) {
										justNull = true;
										fields.append( "^" );
									}

									fields.append( j + 1 ); // increment field so we count from 1 not Zero

									if ( errorFieldUnderline && justNull == false ) {

										if ( lineLength > 0 && fieldErr ) {
											Arrays.fill( errorUnderline, stringStart[ j ] , stringEnd[ j ], '^' );
										} else {
											outInfo.append("\n\n").append(lineNumber).append(":\n").append("  < line is only a newline character >\n");
										}
									}
								} 


							} // end of checking field by field

							// now only for case of allowed extra fields && warning on
							if (errorFieldUnderline && ruleOption.getExtraFieldCount() >= 1 && ruleOption.getWarnExtraFields() ) {
								for ( ; j < actualFieldCount; j++ ) {
									Arrays.fill( errorUnderline, stringStart[ j ] , stringEnd[ j ], '?' );
								}
							} 

							if ( hadError == false && fieldErr == true ) {
								errCount++; 
								Validate.rc( 2 ); // record error
							}

						}
					}  

					if ( hadError == false && fieldErr == false ) {
						// for debugging
						if ( ruleOption.getShowValidData() == true ) {
							outInfo.append("\n\n").append(lineNumber).append(": valid\n").append(line).append("\n");
						}
						continue;
					}

					// how to report findings on error				
					if ( (hadFirstError == false) && (ruleOption.getErrorReportDetails() != 'e') ) {
						hadFirstError = true;
					}

					switch ( ruleOption.getErrorReportDetails() ) {
					case 'n':
						outInfo.append(lineNumber).append(",");
						break;
					case 'l':
						outInfo.append(line);
						break;
					case 'a':

						if ( fieldErr ) {
							outInfo.append("\n").append(lineNumber);
						}

						if ( ruleOption.getErrorFieldUnderline() ) {

							if ( fieldErr == true ) {

								// user wants bad fields underlined with '^'
								// we have a line (with correct field count) so can report it

								outInfo.append(fields).append("\n").append(line).append("\n");
								// its possible there was an error but nothing to underline because of a null field

								if ( hadFirstError ) {
									outInfo.append( new String( errorUnderline ) );
								}

							} 
						}

						break;
					default:
						// got nothing, so show nothing
						break;
					}

					if ( ruleOption.getErrorReportDetails() == 'l' ) {	
						outInfo.append("\n");
					}

				} // end of while

			} catch ( final IOException ioe ) {
				System.err.println( "\nfatal: can't read input file <" + fileName + ">, RULE with id <" + ruleID + ">. "  + ioe.getMessage() );
				System.exit( 50 );	
			} catch ( final PatternSyntaxException pse) {
				System.err.println( "\nfatal: option <delimiterString> RULE with id <" + ruleID + "> does not compile. " );
				System.exit( 50 );	
			} finally {
				br = null;
				inputFile = null;
			}

			// clean up 
			nvpHM = null;
		}	// end of for fileList 

	}

	/*
	 * doCustom - similar to NV formatting, but with less restrictions on the name; more flexibilities on  checking
	 */
	private void doCustom() {

		m = null; // for matcher
		lineSkipRegex = null;
		replaceArray = new String[2];
		replaceRegex = null;
		lineSkipRegexExists = false;
		lineCheckRegexExists = false;
		lineCheckPattern = null;
		lineCheckRegex = null;
		int itemsChecked = 0;

		// handle the line skip regex pattern, to be used later
		if ( (lineSkipRegex = ruleOption.getLineSkipRegex()) != null ) {
			lineSkipRegexExists = true;

			try {
				lineSkipPattern = Pattern.compile( lineSkipRegex );
			} catch (final PatternSyntaxException e) {
				System.err.println( "\nerror: RULE with id <" + ruleID + "> had option <regexCommentRegex>, which did not compile. " + e.getMessage());
				System.exit( 15 );
			}
		}

		// handle editing
		// gets all options for prefix, suffix, replace
		genericLineEdittingPrep();

		if ( ! Validate.testMode ) {

			// in testMode we may not have the real file, so we can't pass to java
			File inputFile = null;	

			if ( (nvpHM == null) || nvpHM.isEmpty() ) { 
				System.err.println( "\nfatal: no REGEX data was found in RULE with id <" + ruleID + ">. " );
				System.exit( 15 );
			}

			StringBuilder outDupsNameVar = null;
			// iterate over the file list of name=value files in this Rule
			for ( final String file : fileList ) {
				// need clean for each file tested
				outInfo = new StringBuilder( 100 );
				outUnchecked = new StringBuilder( 100 );
				String line = null;
				String[] nvArray = new String[2];
				itemsChecked = 0;
				errCount = 0;
				
				if ( file == null ) {
					if ( ruleOption.getWarnRuleWithoutFile() ) {
						System.err.println( "\nwarning: RULE with id <" + ruleID + "> has no <file=> specified; RULE is skipped.");
					}
					continue; // nothing else we can do with no file
				}

				if ( nvRegexUsed != null ) { // so we can start fresh for next file
					nvRegexUsed = new ArrayList<String>();
				}

				outDupsNameVar = new StringBuilder(100); // declare here so its fresh for each file
				lineNumber = 0;	

				try {
					/*
					 * we need to read each non-comment line in the Custom file and test	
					 */

					if ( (dir = ruleOption.getDirPathString()) != null ) {
						f = dir + file;  
					} else {
						f = file;
					}

					inputFile = new File( f );
					br = new BufferedReader( new FileReader( inputFile ) );

					short lineNumber = 0;
					itemsChecked = 0;

					while ( true ) {

						line = br.readLine();

						if ( line == null ) {
							break; // we're done
						} 

						lineNumber++;

						// will handle backslash continued lines if needed
						/*
						 * STEP 1. must do multi-line first so we do't loose info
						 */
						final String strReturned = continuationProcessor( line ) ;

						if ( strReturned.length() == 0 ) {
							continue;
						} else {
							line = strReturned;
						}

						/*
						 * STEP 2. first hurdle is see if its a comment/ignore line to be skipped
						 */
						// if comment skip it
						if ( lineSkipRegexExists ) {

							lineSkipMatcher = lineSkipPattern.matcher( line );
							if ( lineSkipMatcher.find() ) {
								continue;
							}

						}

						/*
						 * use of genericLineEdditing to adjust line before validation
						 */

						// Step 2A.
						// custom only has this
						if ( lineReplaceFlag ) {
							//line = line.replaceAll( replaceArray[0], replaceArray[1]);
							Pattern p = Pattern.compile( replaceArray[0] );
							Matcher mm = p.matcher( line );
							if ( mm.find() ) {
								line = mm.replaceAll( replaceRegex );
							}
						}

						/*
						 * Step 3A & B we can trim off the start and/or end of each line BEFORE we look at the remainder "nv" pair
						 */
						if ( linePrefixRegexExists || lineSuffixRegexExists ) {
							m = linePrefixSuffixPattern.matcher( line );
							line = m.replaceAll( "" );
						}

						/*
						 * Step 4. OK but we could still have more than we are interested in so last global check
						 * determines if we really parse the line
						 */
						//  use it to decide skip it or check it
						if ( lineCheckRegexExists ) {

							lineCheckMatcher = lineCheckPattern.matcher( line );
							if (! lineCheckMatcher.find() ) {
								continue;
							}
						}
						// end of genericLineEditting

						// for debugging let user see what is being checked
						if ( ruleOption.getLineShow() == true ) {
							outInfo.append("\nvalidating: <" + lineNumber + ">: " + line);
						}

						/* 
						 *  ok we really are interested in this line so parse it
						 */
						itemsChecked++;

						// tokenize the line
						nvArray = line.split(ruleOption.getDelimiterRegex(), 2); // user must be careful if they have spaces
						nvArray[0] = nvArray[0].trim();

						if ( nvArray.length > 2 ) {
							System.err.println("\nerror: invalid format for NAME<delimiter>VALUE on line <" + lineNumber + "> line <" + line + ">.");
							Validate.rc( 2 ); // recorded error level
							return;
						} else if ( (nvArray.length == 2) && (nvArray[0] == null) && (nvArray[1] == null) ) {
							System.err.println("\nerror: invalid format for NAME<delimiter>VALUE on line <" + lineNumber + ">, both NAME and VALUE are null.");
							Validate.rc( 2 ); // recorded error level
							return;
						} else if ( (nvArray.length == 2) && (nvArray[0] == null)  ) {
							System.err.println("\nerror: invalid format for NAME<delimiter>VALUE on line <" + lineNumber + ">, NAME is null.");
							Validate.rc( 2 );// recorded error level
							return; 
						} else if ( (nvArray.length == 2) && (nvArray[1] == null)  ) {
							System.err.println("\nerror: invalid format for NAME<delimiter>VALUE on line <" + lineNumber + ">, VALUE is null.");
							Validate.rc( 2 ); // recorded error level
							return;
						} 


						/*
						 *    so the nvArray holds the name <regex> value that we will VALIDATE for the user
						 *    nvRegexUsed - names from the users input need to track for missing names
						 *    the nvpHM holds the names and regexes that we will TEST TO
						 *    
						 *    there are multiple outcomes
						 *    silence - if what we expect to VALIDATE matches the TEST
						 *    error - if what we expect fails the match
						 *    warning - (depends on options) if we did NOT FIND something that we had a TEST for
						 *    waring - (depends on options) if we found a second (or more) occurrence of the name to TEST (a duplicate)
						 *    warning - (depends on options) if the user file had a name that we did NOT test
						 */

						// see if its a name in the users file
						// yes the regexUsed means it WAS used to check a name already - so the name is a dup
						// not the regex (regex duplicates were checked in Regex object
						if ( (nvRegexUsed != null) && nvRegexUsed.contains( nvArray[0] ) ) {
							// we have a duplicate
							if ( ruleOption.getWarnDuplicates() ) {

								if ( outDupsNameVar.length() > 0 ) {
									outDupsNameVar.append(", ").append( nvArray[0] );
								} else {
									outDupsNameVar.append( nvArray[0] );
								}
							}

						} else {
							// we need to save the name of NV pair we had in the file being examined for future
							if ( nvArray != null ) {
								nvRegexUsed.add( nvArray[0] ); // saved for missing test at end
							}
						}

						/*
						 * if it was in a user file but NOT in the config file, its extra
						 */
						if ( nvpHM.containsKey( nvArray[0] ) == false ) {
							itemsChecked--; // since it was already counted
							// we have an extra property
							if ( outUnchecked.length() > 0 ) {
								outUnchecked.append(", ").append( nvArray[0] );
							} else {
								outUnchecked.append( nvArray[0] );
							}

							continue;
						}	

						// retrieve the object so we can access the regex
						nvp = nvpHM.get( nvArray[0] );

						// do WORK - based on the correct name object

						if ( ! nvp.validateValueWithRegex( nvArray[1] ) ) {
							errCount++;

							switch ( ruleOption.getErrorReportDetails() ) {
							case 'n':
								outInfo.append(lineNumber).append(",");
								break;
							case 'l':
								outInfo.append("\n").append(nvArray[0]).append("=").append(nvArray[1]);
								break;
							case 'a':
								outInfo.append("\n").append(lineNumber).append(": fail\n").append("NAME <").append(nvArray[0]).append("> VALUE <").
								append(nvArray[1]).append("> does not match regex: ").append(nvp.getValue()).append("\n");
								break;
							default:
								// got nothing, so show nothing
								break;
							}

							if ( ruleOption.getErrorReportDetails() == 'n' ) {	
								outInfo.append("\n");
							}

						} else {
							if ( ruleOption.getShowValidData() == true ) {
								outInfo.append("\n").append(nvp.getLineNumber()).append(": valid\nNAME <").append(nvArray[0]).append(">\n");
							}
						}
					} // end of while line reading

				} catch ( final IOException io ) {
					System.err.println( "\nfatal: error reading file <" + f + "> in RULE with id <" + ruleID + ">. " + io.getMessage() );
					System.exit( 19 );	
				} catch ( final IllegalArgumentException iae ) {
					System.err.println( "\nfatal: error reading file <" + f + "> in RULE with id <" + ruleID + ">. " + iae.getMessage() );
					System.exit( 19 );
				}

				checkEOFstatus( outInfo.toString(), itemsChecked, errCount, outUnchecked.toString(), outDups.toString(), outDupsNameVar.toString(), f );
			}

		}

		// clean up 
		nvpHM = null;
	}


	/*
	 * process the file(s) to be validated using the formatting and processing for a LINE
	 */
	private void doLine() {

		String lineSkipRegex = null;
		char[] lineUnderline = null;
		lineCheckRegexExists = false;
		lineSkipRegexExists = false;
		lineCheckRegex = null;
		Pattern lineCheckPattern = null;

		m = null; // for matcher

		if ( lineFormatRegexList.isEmpty() ) {
			System.err.println( "\nfatal: there were no saved, regexes for rule with ID <" + ruleID + ">");
			System.exit( 96 );
		}

		if ( (lineSkipRegex = ruleOption.getLineSkipRegex()) != null ) {
			lineSkipRegexExists = true;

			try {
				lineSkipPattern = Pattern.compile( lineSkipRegex );
			} catch (final PatternSyntaxException e) {
				System.err.println( "\nerror: RULE with id <" + ruleID + "> had option <regexCommentRegex>, which did not compile. " + e.getMessage());
				System.exit( 15 );
			}
		}

		if ( (lineCheckRegex = ruleOption.getLineCheckRegex()) != null ) {
			lineCheckRegexExists = true;
			lineCheckPattern = Pattern.compile( lineCheckRegex ); // already tested in options
		}

		// handle editing
		// gets all options for prefix, suffix, replace
		genericLineEdittingPrep();

		// can have many files
		for (final String fileName : fileList) {
			errCount = 0;
			
			if ( fileName == null ) {
				if ( ruleOption.getWarnRuleWithoutFile() ) {
					System.err.println( "\nwarning: RULE with id <" + ruleID + "> has no <file=> specified; RULE skipped.");
				}
				continue; // nothing we can do
			}

			if ( (dir = ruleOption.getDirPathString()) != null ) {
				f = dir + fileName;  
			} else {
				f = fileName;
			}

			try {
				br = new BufferedReader( new FileReader( new File( f ) ));
				outInfo = new StringBuilder( 100 );
				errCount = 0;
				lineNumber = 0;

				while ( true ) {

					line = br.readLine();

					if ( line == null ) {
						// file is done lets close and then check status

						checkEOFstatus( outInfo.toString(), lineNumber, errCount, null, f ); // sends DATA file
						break;
					}

					// see if user wants any lines skipped
					if ( lineSkipRegexExists ) {

						lineSkipMatcher = lineSkipPattern.matcher( line );
						if ( lineSkipMatcher.find() ) {
							continue;
						}
					}

					/*
					 * use of genericLineEdditing to adjust line before validation
					 */

					if ( lineReplaceFlag ) {
						//line = line.replaceAll( replaceArray[0], replaceArray[1]);
						Pattern p = Pattern.compile( replaceArray[0] );
						Matcher mm = p.matcher( line );
						if ( mm.find() ) {
							line = mm.replaceAll( replaceRegex );
						}
					}

					/*
					 *  we can trim off the start and/or end of each line BEFORE we look at the remainder "nv" pair
					 */
					if ( linePrefixRegexExists || lineSuffixRegexExists ) {
						m = linePrefixSuffixPattern.matcher( line );
						line = m.replaceAll( "" );
					}

					/*
					 * OK but we could still have more than we are interested in so last global check
					 * determines if we really parse the line
					 */
					//  use it to decide skip it or check it
					if ( lineCheckRegexExists ) {

						lineCheckMatcher = lineCheckPattern.matcher( line );
						if (! lineCheckMatcher.find() ) {
							continue;
						}
					}
					// end of genericLineEditting

					//  use it to decide skip it or check it
					if ( lineCheckRegexExists ) {

						lineCheckMatcher = lineCheckPattern.matcher( line );
						if (! lineCheckMatcher.find() ) {
							continue;
						}
					}

					lineNumber++; // we intend to check

					// for debugging let user see what is being checked
					if ( ruleOption.getLineShow() == true ) {
						outInfo.append("\nvalidating: <" + lineNumber + ">: " + line);
					}

					// set line clean in case its needed
					if ( ruleOption.getValidLineUnderline() && line.length() > 0 ) {
						lineUnderline = new char[ line.length() ];
						Arrays.fill( lineUnderline, ' ');
					}
					// we have a one line to many regex so we try each regex if necessary

					boolean hadMatch = false;
					int arrayIndex = 0; // track which regex/number pair was being used

					// check if line range is appropriate
					for ( Pattern reg : lineFormatRegexList ) {

						// first see if we have line range restriction
						if ( lineNumber < fromLineList.get(arrayIndex) ) {
							arrayIndex++;
							continue; // line is NOT within range
						}

						if ( toLineList.get(arrayIndex) != 0 && lineNumber > toLineList.get(arrayIndex) ) {
							arrayIndex++;
							continue; // line is NOT within range
						}

						// ok within range or range is entire file
						m = reg.matcher( line );

						if ( m.find() ) {
							hadMatch = true;
							break;
						}

						arrayIndex++;
					}


					if ( hadMatch == false ) {
						errCount++;
						Validate.rc( 2 ); // record error

						switch ( ruleOption.getErrorReportDetails() ) {
						case 'n':
							outInfo.append(lineNumber).append(",");
							break;
						case 'l':
							outInfo.append(line).append("\n");
							break;
						case 'a':
							outInfo.append("\n").append(lineNumber).append(": fail\n").append(line).append("\n");
							break;
						default:
							// got nothing, so show nothing
							break;
						}

					} else {

						if ( ruleOption.getShowValidData() == true ) {
							outInfo.append("\n").append(lineNumber).append(": valid(").append(arrayIndex + 1).append(")\n").append(line).append("\n");
							// and see if they want underlining

							if ( ruleOption.getValidLineUnderline() == true && line.length() > 0 ) {
								Arrays.fill( lineUnderline, m.start(), m.end(), '=');
								if ( lineUnderline != null ) {
									String tmp_s = new String(lineUnderline);
									outInfo.append( tmp_s ).append("\n\n");
									tmp_s = null;
								}
							}
						}
					}

				} // done while reading lines in file
			} catch ( final IOException ioe ) {
				System.err.println( "\nfatal: can't read input file <" + fileName + ">, RULE with id <" + ruleID + ">. " + ioe.getMessage() );
				System.exit( 15 );	
			}
		}

		lineFormatRegexList = null; // clear for reuse	

	} // end of doLine

	/*
	 * process the file(s) to be validated using the formatting and processing for a LINE
	 * similar to Lines 
	 */
	 
	private void doValueLine() {
		
		char[] lineUnderline = null;
		m = null; // for matcher
		
		if ( Validate.valueMode == false ) {
			System.out.println( "warning: -v switch was not used so skipping RULE with Id <" + ruleID + ">");
			return;
		}

		if ( lineFormatRegexList.isEmpty() ) {
			System.err.println( "\nfatal: there were no saved, regexes for rule with ID <" + ruleID + ">");
			System.exit( 96 );
		}

		// handle editing
		// gets all options for prefix, suffix, replace
		genericLineEdittingPrep();

		try {
			outInfo = new StringBuilder( 100 );
			errCount = 0;
			lineNumber = 1;

			// this is the line to test;
			// there is no file
			line = Validate.valueString;
					
			/*
			 * use of genericLineEdditing to adjust line before validation
			 */

			if ( lineReplaceFlag ) {
				Pattern p = Pattern.compile( replaceArray[0] );
				Matcher mm = p.matcher( line );
				if ( mm.find() ) {
					line = mm.replaceAll( replaceRegex );
				}
			}

			/*
			 *  we can trim off the start and/or end of each line BEFORE
			 * we look at the remainder
			 */
			 if ( linePrefixRegexExists || lineSuffixRegexExists ) {
				m = linePrefixSuffixPattern.matcher( line );
				line = m.replaceAll( "" );
			} 


			// for debugging let user see what is being checked
			if ( ruleOption.getLineShow() == true ) {
				outInfo.append("\nvalidating: <" + lineNumber + ">: " + line);
			}

			// set line clean in case its needed
			// set line clean in case its needed
			if ( ruleOption.getValidLineUnderline() && line.length() > 0 ) {
				lineUnderline = new char[ line.length() ];
				Arrays.fill( lineUnderline, ' ');
			}
			// we have a one line to many regex so we try each regex if necessary

			boolean hadMatch = false;
			int arrayIndex = 0; // track which regex/number pair was being used

			// check if line range is appropriate
			for ( Pattern reg : lineFormatRegexList ) {

				// first see if we have line range restriction
				if ( lineNumber < fromLineList.get(arrayIndex) ) {
					arrayIndex++;
					continue; // line is NOT within range
				}

				if ( toLineList.get(arrayIndex) != 0 && lineNumber > toLineList.get(arrayIndex) ) {
					arrayIndex++;
					continue; // line is NOT within range
				}

				m = reg.matcher( line );
				
				if ( m.find() ) {
					hadMatch = true;
					break;
				}

				arrayIndex++;
			}
		

			if ( hadMatch == false ) {
				errCount++;
				Validate.rc( 2 ); // record error

				switch ( ruleOption.getErrorReportDetails() ) {
				case 'n':
					outInfo.append(lineNumber).append(",");
					break;
				case 'l':
					outInfo.append(line).append("\n");
					break;
				case 'a':
					outInfo.append("\n").append(lineNumber).append(": fail\n").append(line).append("\n");
					break;
				default:
					// got nothing, so show nothing
					break;
				}

			} else {

				if ( ruleOption.getShowValidData() == true ) {
					outInfo.append("\n").append(lineNumber).append(": valid(").append(arrayIndex + 1).append(")\n").append(line).append("\n");
					// and see if they want underlining

					if ( ruleOption.getValidLineUnderline() == true && line.length() > 0 ) {
						Arrays.fill( lineUnderline, m.start(), m.end(), '=');
						if ( lineUnderline != null ) {
							String tmp_s = new String(lineUnderline);
							outInfo.append( tmp_s ).append("\n\n");
							tmp_s = null;
						}
					}
				}
			}

		} catch ( ArrayIndexOutOfBoundsException e  ) {
			System.err.println( "\nfatal: array out of bounds in doValueLines, RULE with id <" + ruleID + ">. " );
			System.exit( 15 );
		}

		checkEOFstatus( outInfo.toString(), lineNumber, errCount, null, null );
	} // end doValueLine

	/*
    *
* a delimited file gets a regex for each column in the data
* THIS section is for command line entry via -V and using the VALUE_DELIMITED
*/

	private void doValueDelimited() {

		boolean errorFieldUnderline = ruleOption.getErrorFieldUnderline();
		char[] errorUnderline = null;
		char[] temp = null;
		m = null;
		lineNumber = 1;

		if ( Validate.valueMode == false ) {
			System.out.println( "warning: -v switch was not used so skipping RULE with Id <" + ruleID + ">");
			return;
		}
		
		try {

			int expectedFieldCount = patternListArray.length; // number or Regexes in block
			// set size appropriately, but if extra fields are found java will grow size
			int ArrayListInitialSize = expectedFieldCount + 1 +  ruleOption.getExtraFieldCount();
			int actualFieldCount;
			boolean hadError = false;
			final StringBuilder delimiterRegexInParens = new StringBuilder();
			delimiterRegexInParens.append( "(" ).append( ruleOption.getDelimiterRegex() ).append( ")" );
			final Pattern delimiterPattern = Pattern.compile( delimiterRegexInParens.toString() );
			ArrayList<Integer> delimiterStart = new ArrayList<Integer>( ArrayListInitialSize );
			ArrayList<Integer> delimiterEnd = new ArrayList<Integer>( ArrayListInitialSize );
			int delimiterIndex = 0;
			int lineLength = 0;
			StringBuilder fields;
			String[] lineArray = null;
			Matcher delimiterMatcher = null;
			outInfo = new StringBuilder( 500 );
			errCount = 0;
			String EOLdelimiterAtEOL = ruleOption.getDelimiterRegex() + '$';
			Pattern EOLPat = Pattern.compile( EOLdelimiterAtEOL ); // will use on every non-null (non-comment too) line of input

			// handle editing
			// gets all options for prefix, suffix, replace
			genericLineEdittingPrep();

			fields = new StringBuilder( 500 );

			hadError = false;
			actualFieldCount = 0;
			lineLength = 0;

			line = Validate.valueString;

			/*
 			 * use of genericLineEdditing to adjust line before validation
			 */

			if ( lineReplaceFlag ) {
				//line = line.replaceAll( replaceArray[0], replaceArray[1]);
				Pattern p = Pattern.compile( replaceArray[0] );
				Matcher mm = p.matcher( line );
				if ( mm.find() ) {
					line = mm.replaceAll( replaceRegex );
				}
			}

			/*
			 *  we can trim off the start and/or end of each line BEFORE we
			 * look at the remainder of the input
			 */
			if ( linePrefixRegexExists || lineSuffixRegexExists ) {
				m = linePrefixSuffixPattern.matcher( line );
				line = m.replaceAll( "" );
			}

			// end of genericLineEditting

			// for debugging let user see what is being checked
			if ( (ruleOption.getLineShow() == true)) {
				outInfo.append("\nvalidating: <").append(lineNumber).append(">: ").append(line);
			}

			lineLength = line.length();
			delimiterMatcher = delimiterPattern.matcher( line );

			/*
			 * expectedFieldCount=the number or regexes we have, thus the number of fields=actualFieldCount we expect
			 * BUT
			 * we have multiple end of line delimiter situations e.g.
			 * expectedFieldCount=2 so we expect 2 fields, we could have
			 * ONE:TWO
			 * ONE:TWO:
			 * less than 1 : would be an error TOO FEW FIELDS
			 * more than 2 : would be an error TOO MANY
			 * if : = 1 EOLdelimiter=false OK
			 * if : = 2 EOLdelimiter=true OK
			 * if : = 1 or 2 AND EOLdelimiter=optional OK
			 */

			// lets see what the input line ends with

			Matcher EOLmatch = EOLPat.matcher( line );

			if ( EOLmatch.find() ) {
				// if here line ends in a delimiter
				if ( "false".equals(ruleOption.getEOLdelimiter() ) ) {
					// line is no good since no EOLdelimiter wanted and its there
					outInfo.append("\n").append(lineNumber).append("\n").append(line).append("\n  <error, final delimiter not expected>\n");
					errCount++;
					hadError = true;
					Validate.rc( 2 ); // record error
				}
				// if here: we had an EOLdelimiter lets trim it off - but the line might not be correct
				line = line.replaceAll( EOLdelimiterAtEOL, "");

			} else {
				// EOLdelimiter is NOT there: if optional its ok, but if required its error
				if ( "true".equals( ruleOption.getEOLdelimiter() ) ) {
					outInfo.append("\n").append(lineNumber).append(":\n").append(line).append("\n  <error, final delimiter missing>\n");
					errCount++;
					hadError = true;
					Validate.rc( 2 ); // record error
				}
			}

			EOLmatch = null;

			// since EOL is taken care of now we need to be concerned with actual fields of data
			// ***** so the rest below is ONLY needed if we have NOT had an error
			boolean fieldErr = false;

			if ( hadError == false ) {
				delimiterIndex = 0;

				if ( errorFieldUnderline ) {
					temp = new char[ lineLength ];
					Arrays.fill( temp,  '0' );
				}

				delimiterIndex = 0;
				while ( delimiterMatcher.find() ) { // get the start and end of each delimiter

					delimiterStart.add( delimiterMatcher.start() );
					delimiterEnd.add( delimiterMatcher.end() );

					if ( errorFieldUnderline ) {
						Arrays.fill( temp, delimiterStart.get(delimiterIndex), delimiterEnd.get(delimiterIndex), '1');
					}

					delimiterIndex++;
				}

				lineArray = line.split( ruleOption.getDelimiterRegex(), delimiterIndex + 1);

				// from the delimiterArray we can build a str + start/end arrays needed only for underline
				int[] stringStart = null;
				int[] stringEnd = null;

				boolean done = false;
				actualFieldCount = lineArray.length;

				/*
				 * because we allow a trailing field in certain cases of EOL we need to
				 * check more that actual and expected count
				 */

				if ( actualFieldCount < expectedFieldCount ) {
					// failure
					// add a test in case it was just whitespace

					// only in the "all case"
					if ( ruleOption.getErrorReportDetails() == 'a' ) {

						if ( line.matches("\\s*") ) {
							outInfo.append("\n\n").append(lineNumber).append(
									":  << error, input is just whitespace, therefore less fields than expected for the give regexes. Consider adding to lineSkipRegex. >>\n");
						} else {
							outInfo.append("\n\n").append(lineNumber).append(":\n").append(line).append("\n  <error, less fields than expected for the given regexes>\n");
						}

					}

					errCount++;
					hadError = true;
					Validate.rc( 2 ); // record error
					done = true;

				} else if ( actualFieldCount == expectedFieldCount ) {
					; // perfect just check fields
				} else {
					// so actual is greater
					// special case: the user could want extra end fields but not test them

					if ( ruleOption.getExtraFieldCount() >= 1 ) {
						if ( actualFieldCount > expectedFieldCount + ruleOption.getExtraFieldCount() ) {
							// too many call it an error
							// only in the "all case"
							if ( ruleOption.getErrorReportDetails() == 'a' ) {
								outInfo.append("\n").append(lineNumber).append(":\n").append(line).append("\n  <error, <").append(
										actualFieldCount - expectedFieldCount).append("> extra field(s) exceed the count of <").append(
												ruleOption.getExtraFieldCount() ).append("> that was specified>");
							}

							errCount++;
							hadError = true;
							Validate.rc( 2 );

						} else {
							if (ruleOption.getWarnExtraFields() == true ) {
								outInfo.append("\n").append(lineNumber).append(":\n").append(line).append("\n  <warning, <").append(
										actualFieldCount - expectedFieldCount).append("> extra field(s), only the first <").append(
												expectedFieldCount).append("> are validated>");

							}
							Validate.rc( 1 );
						}

					} else if ( ruleOption.getExtraFieldCount() < 0 ) {
						; // just ignore them without warning
					} else {
						// by default extra == 0 so the user has an error to deal with

						// only in the "all case"
						if ( ruleOption.getErrorReportDetails() == 'a' ) {
							outInfo.append("\n").append(lineNumber).append(":\n").append(
									line).append("  <error, too many fields for the given regexes> ");
						}

						errCount++;
						hadError = true;
						Validate.rc( 2 ); // record error
						done = true;

					}


				} // end of if for had delimiter

				// before we check if each field matched we want to know the
				// position of strings IF
				// the user wants underlining else don't bother


				// we had the right delimiter state above so need to do fields
				if ( ! done ) {

					if ( errorFieldUnderline ) {
						errorUnderline = new char[ lineLength ];
						Arrays.fill( errorUnderline, ' ' ); // start clean and update to ^ as needed
						stringStart = new int[ lineArray.length ];
						stringEnd = new int[ lineArray.length ];
						int index = 0;
						boolean marking = false;
						int p = 0;


						for ( p = 0; p < lineLength; p++ ) {
							// walk the temp[] and set string start/end values
							if ( marking == false ) {
								if ( temp != null && temp[ p ] == '0' ) {
									marking = true;
									stringStart[ index ] = p;
								}
							} else {
								// marking = true
								if ( temp[ p ] == '1' ) {
									stringEnd[ index ] = p;
									marking = false;
									index++;
								}
							}
						}

						if ( marking == true ) {
							// need to close the stringEnd
							stringEnd[ index ] = p;
						}

					} // end if errorFieldUnderline

					// go field by field
					// we will still use the groups captured to determine string lengths
					int j = 0;
					for ( ; j < expectedFieldCount; j++ ) {

						boolean justNull = false;
						m = ((Pattern)patternListArray[ j ]).matcher( lineArray[ j ] ); // reset the matcher for the next pattern

						if ( ! m.find() ) { // NO-Match=error

							fieldErr = true;
							Validate.rc( 2 ); // record error

							if ( fields.length() > 0 ) {
								fields.append( "," );
							} else {
								fields.append( ":" );
							}

							// special case mark a null field that failed
							if ( lineArray[j].length() == 0 ) {
								justNull = true;
								fields.append( "^" );
							}

							fields.append( j + 1 ); // increment field so we count from 1 not Zero

							if ( errorFieldUnderline && justNull == false ) {

								if ( lineLength > 0 && fieldErr ) {
									Arrays.fill( errorUnderline, stringStart[ j ] , stringEnd[ j ], '^' );
								} else {
									outInfo.append("\n\n").append(lineNumber).append(":\n").append("  < line is only a newline character >\n");
								}
							}
						}

					} // end of checking field by field


					// now only for case of allowed extra fields && warning on
					if (errorFieldUnderline && ruleOption.getExtraFieldCount() >= 1 && ruleOption.getWarnExtraFields() ) {
						for ( ; j < actualFieldCount; j++ ) {
							Arrays.fill( errorUnderline, stringStart[ j ] , stringEnd[ j ], '?' );
						}
					}

					if ( hadError == false && fieldErr == true ) {
						errCount++;
						Validate.rc( 2 ); // record error
					}

				}
			}

			if ( hadError == false && fieldErr == false ) {
				// for debugging
				if ( ruleOption.getShowValidData() == true ) {
					outInfo.append("\n\n").append(lineNumber).append(": valid\n").append(line).append("\n");
				}
				
			}

			// how to report findings on error      
			if ( (hadFirstError == false) && (ruleOption.getErrorReportDetails() != 'e') ) {
				hadFirstError = true;
			}

			switch ( ruleOption.getErrorReportDetails() ) {
			case 'n':
				outInfo.append(lineNumber).append(",");
				break;
			case 'l':
				outInfo.append(line);
				break;
			case 'a':

				if ( fieldErr ) {
					outInfo.append("\n").append(lineNumber);
				}

				if ( ruleOption.getErrorFieldUnderline() ) {

					if ( fieldErr == true ) {

						// user wants bad fields underlined with '^'
						// we have a line (with correct field count) so can report it

						outInfo.append(fields).append("\n").append(line).append("\n");
						// its possible there was an error but nothing to underline because of a null field

						if ( hadFirstError ) {
							outInfo.append( new String( errorUnderline ) );
						}

					}
				}

				break;
			default:
				// got nothing, so show nothing
				break;
			}

			if ( ruleOption.getErrorReportDetails() == 'l' ) {
				outInfo.append("\n");
			}

			checkEOFstatus( outInfo.toString(), lineNumber, errCount, "", f); // sends the DATA file

		} catch ( final PatternSyntaxException pse) {
		System.err.println( "\nfatal: option <delimiterString> RULE with id <" + ruleID + "> does not compile. " );
		System.exit( 50 );
	}

	
}       // end of for fileList

       
	// its a java properties file. Use java to check it, then apply optional
	// tests as given
	private void doJava() {
		
		int propCount = 0;
		lineSkipRegexExists = false;
		lineCheckRegexExists = false;

		if ( (lineSkipRegex = ruleOption.getLineSkipRegex()) != null ) {
			lineSkipRegexExists = true;

			try {
				lineSkipPattern = Pattern.compile( lineSkipRegex );
			} catch ( PatternSyntaxException e ) {
				// already tested when read
				System.err.println( "\nfatal: failed to compile <lineSkipRegex> from rule ID <" + ruleID + ">.");
				System.exit( 55 );
			}
		}

		if ( (lineCheckRegex = ruleOption.getLineCheckRegex()) != null ) {
			lineCheckRegexExists = true;

			try {
				lineCheckPattern = Pattern.compile( lineCheckRegex );
			} catch ( PatternSyntaxException e ) {
				// already tested when read
				System.err.println( "\nfatal: failed to compile <lineCheckRegex> from rule ID <" + ruleID + ">.");
				System.exit( 55 );
			}
		}

		if ( ! Validate.testMode ) {
			// in testMode we may not have the real file, so we can't pass to java
			final Properties prop = new Properties();
			FileInputStream fin = null;

			if ( (nvpHM == null) || nvpHM.isEmpty()) {
				System.err.println( "\nfatal: no REGEX data was found in RULE with id <" + ruleID + ">. " );
				System.exit( 15 );
			}

			// iterate over the file list
			// for each property file we are storing the properties using java
			for ( final String file : fileList ) {
				// need clean for each file tested
				outInfo = new StringBuilder( 100 );
				outUnchecked = new StringBuilder( 100 );
				errCount = 0;
				
				if ( file == null ) {
					if ( ruleOption.getWarnRuleWithoutFile() ) {
						System.err.println( "\nwarning: RULE with id <" + ruleID + "> has no <file=> specified; RULE is skipped.");
					}
					continue; // nothing else we can do with no file
				}

				if ( (dir = ruleOption.getDirPathString()) != null ) {
					f = dir + file;  
				} else {
					f = file;
				}

				lineNumber = 0;

				try {
					fin = new FileInputStream( file );
					if ( fin != null ) {
						prop.load( fin );
						fin.close();
					}		
				} catch ( final IOException io ) {
					System.err.println( "\nfatal: error reading file in RULE with id <" + ruleID + ">. " + io.getMessage() );
					System.exit( 15 );	
				} catch ( final IllegalArgumentException iae ) {
					System.err.println( "\nfatal: error reading file in RULE with id <" + ruleID + ">. " + iae.getMessage() );
					System.exit( 15 );
				}

				// lets read it back out as name value pairs
				for (final String propName : prop.stringPropertyNames()) {

					final String propValue = prop.getProperty(propName); 
					propCount++;

					// so we read a name and its value as it was stored from the prop file
					if ( (propName != null) && (propValue != null) ) {

						// inserted - we'll add the same 3 things as other formats. THe opportunity to skip, match and show whats left
						// because these maybe useful while a user is building up a RULE section over time
						// we will just use local flag so we can pre-compile the Pattern to save time

						if ( lineSkipRegexExists ) {
							lineSkipMatcher = lineSkipPattern.matcher( propName );

							if ( lineSkipMatcher.find() ) {
								continue; // user wants to skip
							}
						}

						if ( lineCheckRegexExists ) {
							lineCheckMatcher = lineCheckPattern.matcher( propName );

							if ( ! lineCheckMatcher.find() ) {
								continue; // could not find what user wanted
							}
						}

						if ( (lineSkipRegexExists || lineCheckRegexExists) &&  ruleOption.getLineShow() ) {
							System.out.println("validating: property name <" + propName + ">.");
						}


						// here we are testing to see if the name in the prop file DOES have a NAME=regex
						// if NOT it is an UNchecked and we may need to warn the user
						if ( nvpHM.containsKey( propName ) == false ) {
							// we have an extra property
							if ( outUnchecked.length() > 0 ) {
								outUnchecked.append(", ").append( propName );
							} else {
								outUnchecked.append( propName );
							}

							continue;
						} else {
							// so we do have a name=regex to check it with
							nvp = nvpHM.get(propName);


							// do WORK - 

							if ( ! nvp.validateValueWithRegex( propValue ) ) {
								errCount++;

								switch ( ruleOption.getErrorReportDetails() ) {
								case 'n':
									outInfo.append( nvp.getLineNumber() ).append(",");
									break;
								case 'l':
									outInfo.append(propName).append("=").append(propValue);
									break;
								case 'a': 
									outInfo.append("\n").append(nvp.getLineNumber()).append(": fail\n").append("PROP <").append(propName).
									append("> VALUE <").append(propValue).append("> does not match regex: ").append(nvp.getValue()).append("\n");
									break;
								default:
									// got nothing, so show nothing
									break;
								}

								if ( ruleOption.getErrorReportDetails() == 'l' ) {	
									outInfo.append("\n");
								}

							} else {
								if ( ruleOption.getShowValidData() == true ) {
									outInfo.append("\n").append(nvp.getLineNumber()).append(": valid\n").append(propValue).append("\n");
								}
							}

						} // end else of contaninKey
					}
				} // end prop checking


			}
			checkEOFstatus( outInfo.toString(), propCount, errCount, outUnchecked.toString(), configFileName ); // sends DATA file
		}

		// clean up 
		nvpHM = null;

	} // end doJava

	/*
	 * doNameValue - process the user provided regexes for name=value format checking
	 * processing is similar to javaProperties processing
	 */

	private void doNameValue() {
		lineSkipRegex = null;
		lineCheckRegexExists = false;
		lineCheckRegex = null;
		lineCheckPattern = null;
		lineSkipRegexExists = false;
		int itemsChecked = 0;
		
		if ( (lineSkipRegex = ruleOption.getLineSkipRegex()) != null ) {
			lineSkipRegexExists = true;

			try {
				lineSkipPattern = Pattern.compile( lineSkipRegex );
			} catch (final PatternSyntaxException e) {
				System.err.println( "\nerror: RULE with ruleID <" + ruleID + "> had option <lineSkipRegex>, which did not compile. " + e.getMessage());
				System.exit( 15 );
			}
		}

		// handle editting
		// gets all options for prefix, suffix, replace
		genericLineEdittingPrep();

		if ( ! Validate.testMode ) {

			// in testMode we may not have the real file, so we can't pass to java
			File inputFile = null;	

			if ( (nvpHM == null) || nvpHM.isEmpty() ) {
				System.err.println( "\nfatal: no REGEX data was found in RULE with id <" + ruleID + ">. " );
				System.exit( 15 );
			}

			StringBuilder outDupsNameVar = null;
			// iterate over the file list of name=value files in this Rule
			// iterate over the list of name=value files in this Rule
			for ( final String file : fileList ) {				// need these clean per file tested
				outInfo = new StringBuilder( 100 );
				outUnchecked = new StringBuilder( 100 );
				outDupsNameVar = new StringBuilder( 100 ); 
				String line = null;
				String[] nvArray = new String[2];
				itemsChecked = 0;
				errCount = 0;
				
				if ( file == null ) {
					if ( ruleOption.getWarnRuleWithoutFile() ) {
						System.err.println( "\nwarning: RULE with id <" + ruleID + "> has no <file=> specified; RULE is skipped.");
					}
					continue; // nothing else we can do with no file
				}

				if ( nvRegexUsed != null ) { // so we can start fresh for next file
					nvRegexUsed = new ArrayList<String>();
				}

				lineNumber = 0;

				try {
					/*
					 * we need to read each non-comment line in the Name=Value file and test	
					 */

					if ( (dir = ruleOption.getDirPathString()) != null ) {
						f = dir + file;  
					} else {
						f = file;
					}

					inputFile = new File( f );
					br = new BufferedReader( new FileReader( inputFile ) );
					short lineNumber = 0;
					
					while ( true ) {
						// line - is the line of input from the config file
						line = br.readLine();

						if ( line == null ) {
							break; // we're done
						} 

						lineNumber++;

						// will handle backslash continued lines if needed
						final String strReturned = continuationProcessor( line ) ;

						if ( strReturned.length() == 0 ) {
							continue;
						} else {
							line = strReturned;
						}

						// if comment skip it
						if ( lineSkipRegexExists ) {

							lineSkipMatcher = lineSkipPattern.matcher( line );
							if ( lineSkipMatcher.find() ) {
								continue;
							}
						}

						/*
						 * use of genericLineEdditing to adjust line before validation
						 */

						if ( lineReplaceFlag ) {
							//line = line.replaceAll( replaceArray[0], replaceArray[1]);
							Pattern p = Pattern.compile( replaceArray[0] );
							Matcher mm = p.matcher( line );
							if ( mm.find() ) {
								line = mm.replaceAll( replaceRegex );
							}
						}

						/*
						 *  we can trim off the start and/or end of each line BEFORE we look at the remainder "nv" pair
						 */
						if ( linePrefixRegexExists || lineSuffixRegexExists ) {
							m = linePrefixSuffixPattern.matcher( line );
							line = m.replaceAll( "" );
						}

						/*
						 * OK but we could still have more than we are interested in so last global check
						 * determines if we really parse the line
						 */
						//  use it to decide skip it or check it
						if ( lineCheckRegexExists ) {

							lineCheckMatcher = lineCheckPattern.matcher( line );
							if (! lineCheckMatcher.find() ) {
								continue;
							}
						}
						// end of genericLineEditting


						//  use it to decide skip it or check it
						if ( lineCheckRegexExists ) {

							lineCheckMatcher = lineCheckPattern.matcher( line );
							if (! lineCheckMatcher.find() ) {
								continue;
							}
						}

						// for debugging let user see what is being checked
						if ( ruleOption.getLineShow() == true ) {
							outInfo.append("\nvalidating: <" + lineNumber + ">: " + line);
						}

						itemsChecked++;

						// tokenize the line
						nvArray = line.split("\\s*=", 2); // LEAVE spaces in VALUE
						nvArray[0] = nvArray[0].trim(); // get rid of indent but 

						if ( nvArray.length > 2 ) {
							System.err.println("\nerror: invalid format for NAME=VALUE on line <" + lineNumber + "> line <" + line + ">.");
							Validate.rc( 2 ); // recorded error level
							return;
						} else if ( (nvArray.length == 2) && (nvArray[0] == null) && (nvArray[1] == null) ) {
							System.err.println("\nerror: invalid format for NAME=VALUE on line <" + lineNumber + ">, both NAME and VALUEe are null.");
							Validate.rc( 2 ); // recorded error level
							return;
						} else if ( (nvArray.length == 2) && (nvArray[0] == null)  ) {
							System.err.println("\nerror: invalid format for NAME=VALUE on line <" + lineNumber + ">, NAME is null.");
							Validate.rc( 2 ); // recorded error level
							return; 
						} else if ( (nvArray.length == 2) && (nvArray[1] == null)  ) {
							System.err.println("\nerror: invalid format for NAME=VALUE on line <" + lineNumber + ">, VALUE is null.");
							Validate.rc( 2 ); // recorded error level
							return;
						}

						/*
						 *    so the nvArray holds the name=value that we will VALIDATE for the user
						 *    nvRegexUsed - names from the users input need to track for missing names
						 *    the nvpHM holds the names and regexes that we will TEST TO
						 *    
						 *    there are multiple outcomes
						 *    silence - if what we expect to VALIDATE matches the TEST
						 *    error - if what we expect fails the match
						 *    warning - (depends on options) if we did NOT FIND something that we had a TEST for
						 *    warning - (depends on options) if we found a second (or more) occurence of the name to TEST (a duplicate)
						 *    warning - (depends on options) if the user file had a name that we did NOT test
						 */

						// see if its a name in the users file
						// yes the regexUsed means it WAS used to check a name already - so the name is a dup
						// not the regex (regex dupes were checked in Regex object
						if ( (nvRegexUsed != null) && nvRegexUsed.contains( nvArray[0] ) ) {
							// we have a duplicate
							if ( ruleOption.getWarnDuplicates() ) {

								if ( outDupsNameVar.length() > 0 ) {
									outDupsNameVar.append(", ").append( nvArray[0] );
								} else {
									outDupsNameVar.append( nvArray[0] );
								}
							}

						} else {
							// we need to save the name of NV pair we had in the file being examined for future
							if ( nvArray != null ) {
								nvRegexUsed.add( nvArray[0] ); // saved for missing test at end
							}
						}

						/*
						 * if it was in a user file but NOT in the config file, its extra
						 */

						if ( nvpHM.containsKey( nvArray[0] ) == false ) {

							// we have an extra property
							if ( outUnchecked.length() > 0 ) {
								outUnchecked.append(", ").append( nvArray[0] );
							} else {
								outUnchecked.append( nvArray[0] );
							}

							continue;
						}	

						// retrieve the object so we can access the regex
						nvp = nvpHM.get( nvArray[0] );

						// do WORK - based on the correct name object
						if ( ! nvp.validateValueWithRegex( nvArray[1] ) ) {
							errCount++;

							switch ( ruleOption.getErrorReportDetails() ) {
							case 'n':
								outInfo.append(lineNumber + ",");
								break;
							case 'l':
								outInfo.append("\n").append(nvArray[0]).append("=").append(nvArray[1]);
								break;
							case 'a':
								outInfo.append("\n").append(lineNumber).append(": fail\n").append("NAME <").
								append(nvArray[0]).append("> VALUE <").append(nvArray[1]).
								append("> does not match regex: ").append(nvp.getValue()).append("\n");
								break;
							default:
								// got nothing, so show nothing
								break;
							}

							if ( ruleOption.getErrorReportDetails() == 'n' ) {	
								outInfo.append("\n");
							}

						} else {
							if ( ruleOption.getShowValidData() == true ) {
								outInfo.append("\n").append(nvp.getLineNumber()).append(": valid\nNAME <").append(nvArray[0]).append(">\n");
							}
						}
					} // end of while line reading

				} catch ( final IOException io ) {
					System.err.println( "\nfatal: error reading file <" + f + "> in RULE with ruleID <" + ruleID + ">. " + io.getMessage() );
					System.exit( 19 );	
				} catch ( final IllegalArgumentException iae ) {
					System.err.println( "\nfatal: error reading file <" + f + "> in RULE with ruleID <" + ruleID + ">. " + iae.getMessage() );
					System.exit( 19 );
				}

				checkEOFstatus( outInfo.toString(), itemsChecked, errCount, outUnchecked.toString(), outDups.toString(), outDupsNameVar.toString(), f ); 
				itemsChecked = 0;
			}

		}

		// clean up 
		nvpHM = null;
	} // end doNameValue

	/************************ overloaded methods below **************************/
	/*
	 * we are done reading the users file, now we see what gets reported - if anything in addition to previous
	 * errors reported on
	 * 
	 * Note: this version is ONLY for FORMATS of PROPERTIES, LINES, DELIMITED see next overloaded method
	 */
	private void checkEOFstatus ( String s, int lines, int errors, String Unchecked, String fileName ) {
		// reached EOF of a file to validate
		final char errorReportSummary = ruleOption.getErrorReportSummary();
		char sp = ' ';

		// this is to make the SUMMARY line "greppable" if it has errors
		if ( errors > 0 ) {
			sp = ' ';
			Validate.saveTotalFails( errors );
		} else {
			sp = '\0';
		}

		String title = null; // the properties we want a different title to make it clearer to user
		if ( ruleFormat == format.JAVA  ) {
			title = "PROPERTIES";
		} else if ( ruleFormat == format.LINE || ruleFormat == format.DELIMITED) {
			title = "LINES";
		} else if ( ruleFormat == format.VALUE_LINE || ruleFormat == format.VALUE_DELIMITED) {
			title = "VALUE";
		} else {
			title = "UPDATE_HEADING"; // place holder
		}

		switch ( errorReportSummary ) {
		case 'a':
			if ( f != null ) {
				System.out.println( "\n% ID: " + ruleID + " FILE: " + f + " " + title + ": " + lines + " FAIL: " + errors + sp );
				System.out.println( "    (line numbers refer to file: " + fileName + ")" );   // differs depending on format=value
			} else {
				System.out.println( "\n% ID: " + ruleID + " " +  title + ": " + lines + " FAIL: " + errors + sp );
			}
			break;
		case 'f':
			if ( errors > 0 ) {
				if ( f != null ) {
					System.out.println( "\n% ID: " + ruleID + " FILE: " + f + " " + title + ": " + lines + " FAIL: " + errors + sp);
					System.out.println( "    (line numbers refer to file: " + fileName + ")" );   // differs depending on format=value
				} else {
					// use for valueMode since there is no file!
					System.out.println( "\n% ID: " + ruleID + " " + title + ": " + lines + " FAIL: " + errors + sp);
				}
			}
			break;
		default:
			System.err.println( "\nfatal: programming error invalid <errorReportSummary> in RULE with ruleID <" + ruleID + ">." );
			System.exit( 101 );
			break;
		}

		if ( s.length() > 0 ) {
			// we have some info to report
			System.out.println( s + "\n" ); 
		}

		boolean hadPreviousWarning = false;
		if ( ruleOption.getWarnUncheckedName() && (Unchecked.length() > 0) ) {
			if ( hadPreviousWarning ) {
				System.out.println("\n");
			}
			System.out.println("warning: unchecked NAME(s): " + Unchecked);
			Validate.rc( 1 ); // recorded warning level
			hadPreviousWarning = true;
		}

		// compare regexUsed vs nvpHM the difference is regexes that were NOT used
		// since infoDups is done we'll reuse it

		if ( ! nvRegexUsed.isEmpty() && ruleOption.getWarnRegexUnused() ) {
			boolean gotOne = false;
			final StringBuilder sb = new StringBuilder( 100 );

			if ( hadPreviousWarning ) {
				sb.append("\n");
			}
			sb.append("warning: unused regex(es) for NAME(s): ");

			for(final String str : nvpHM.keySet() ) {

				if ( nvRegexUsed.contains( str ) ) {
					continue;
				} else {

					if ( gotOne ) {
						sb.append(", " + str);
					} else {
						sb.append(str);
						gotOne = true;
					}

				}
			}

			// only print if something to show
			if ( gotOne != false ) {
				System.out.println( sb.toString() );
				gotOne = true;
			}
		}

		return;
	}


	/*
	 * we are done reading the users file, now we see what gets reported - if anything in addition to previous
	 * errors reported on
	 * 
	 * NOTE: this version takes on one more parameter used ONLY for: NameValue, and CUSTOM!
	 */
	private void checkEOFstatus ( String s, int lines, int errors, String Unchecked, String Dups, String NameDups, String fileName ) {
		// reached EOF of a file to validate
		final char errorReportSummary = ruleOption.getErrorReportSummary();
		char sp = ' ';

		// this is to make the SUMMARY line "grep-able" if it has errors
		if ( errors > 0 ) {
			sp = ' ';
			Validate.saveTotalFails( errors );
		} else {
			sp = '\0';
		}

		String title = null; // the properties we want a different title to make it clearer to user
		if ( ruleFormat == format.NAME_VALUE || ruleFormat == format.CUSTOM ) {
			title = "VARIABLES";
		} else {
			title = "UPDATE_HEADING"; // place holder
		}

		switch ( errorReportSummary ) {
		case 'a':
			System.out.println( "\n% ID: " + ruleID + " FILE: " + f + " " + title + ": " + lines + " FAIL: " + errors + sp );
			System.out.println( "    (line numbers (xxx:) refer to file: " + fileName + ")" );   // differs depending on format=value
			
			break;
		case 'f':
			if ( errors > 0 ) {
				System.out.println( "\n% ID: " + ruleID + " FILE: " + f + " " + title + ": " + lines + " FAIL: " + errors + sp);
				System.out.println( "    (line numbers (xxx:) refer to file: " + fileName + ")" ); // differs depending on format=value
			}
			break;
		default:
			System.err.println( "\nfatal: programming error invalid <errorReportSummary> in RULE with ruleID <" + ruleID + ">.");
			System.exit( 101 );
			break;
		}

		if ( s.length() > 0 ) {
			// we have some info to report
			System.out.println( s + "\n"); 
		}

		boolean hadPreviousWarning = false;
		if ( ruleOption.getWarnUncheckedName() && (Unchecked.length() > 0) ) {
			System.out.println("warning: unchecked NAME(s): " + Unchecked);
			Validate.rc( 1 ); // recorded warning level
			hadPreviousWarning = true;
		}

		if ( ruleOption.getWarnDuplicates() && (Dups != null && Dups.length() > 0) ) {
			if ( hadPreviousWarning ) {
				System.out.println("\n");
			}
			System.out.println("warning: duplicated regex(es), the last one was used: " + Dups);
			Validate.rc( 1 ); // recorded warning level
			hadPreviousWarning = true;
		}

		if ( ruleOption.getWarnDuplicates() && (NameDups != null && NameDups.length() > 0) ) {
			if ( hadPreviousWarning ) {
				System.out.println("\n");
			}
			System.out.println("warning: duplicated name(s) in data file, the last one was used: " + NameDups);
			Validate.rc( 1 ); // recorded warning level
			hadPreviousWarning = true;
		}

		// compare regexUsed vs nvpHM the difference is regexes that were NOT used
		// since infoDups is done we'll reuse it

		if ( ! nvRegexUsed.isEmpty() && ruleOption.getWarnRegexUnused() ) {
			boolean gotOne = false;
			final StringBuilder sb = new StringBuilder( 100 );

			if ( hadPreviousWarning ) {
				sb.append("\n");
			}
			sb.append("warning: unused regex(es) for NAME(s): ");

			for(final String str : nvpHM.keySet() ) {

				if ( nvRegexUsed.contains( str ) ) {
					continue;
				} else {

					if ( gotOne ) {
						sb.append(", " + str);
					} else {
						sb.append(str);
						gotOne = true;
					}
				}
			} // end for

			// only print if something to show
			if ( gotOne != false ) {
				System.out.println( sb.toString() );
				gotOne = true;
			}
		}

		return;
	}

	/************************ end of overloaded methods below **************************/

	/*
	 * deals with line IF we have option for lineContinuation
	 */
	String continuationProcessor( String line ) {
		tmpline = null;

		if ( ruleOption.getFileLineContinuation() ) {

			if ( line.endsWith("\\") && ! line.endsWith("\\\\")  ) {

				// its a continuation; need to adjust line and concatenate
				if ( continuationState == false ) {
					// swallow the \
					continuationState = true;
					tmpline = line.substring(0, line.length() - 1 ); // trims the backslash
					lineNumber++;
					return "";
				} else {
					// not the final line, so append and read more
					line = line.trim(); // swallows the start of line WS
					line = line.substring(0, line.length() - 1); // trims the backslash
					tmpline += line;//
					lineNumber++;
					return "";
				}

			} else {

				if ( continuationState ) {
					// the final line now, so trim and use it
					line = line.trim();
					lineNumber++; 
					tmpline += line;
					continuationState = false; // reset
					line = tmpline;
				} else {
					// line used as is
					lineNumber++;
				}
			}

		} else {
			// user doesn't want this feature
			lineNumber++;
		}

		continuationState = false; // reset
		tmpline = null;
		return line;
	}


	/*
	 * used by all formats EXCEPT JavaProperties
	 * user can edit the data line to get it into a more parse-able format for validating
	 */
	void genericLineEdittingPrep() {
		/*
		 * we check for prefix and/or suffix and if so we check for line anchor and assemble one regex for the line
		 */
		linePrefixRegex = ruleOption.getLinePrefixRegex(); 
		if ( linePrefixRegex != null && linePrefixRegex.length() > 0 ) {
			linePrefixRegexExists = true;

			try {
				linePrefixSuffixPattern = Pattern.compile( linePrefixRegex );
			} catch (final PatternSyntaxException e) {
				System.err.println( "\nerror: RULE with id <" + ruleID + "> had option <customePrefixRegex>, which did not compile. " + e.getMessage());
				System.exit( 15 );
			}

			if ( linePrefixRegex.charAt(0) != '^' ) {
				linePrefixSuffixRegex.append("^");  // start the regex
			}

			linePrefixSuffixPattern = null; // no longer needed
			linePrefixSuffixRegex.append( linePrefixRegex ); // add on user input to regex
		}

		// done the prefix IF it existed
		if ( linePrefixRegexExists ) {
			// we have a prefix so append the OR
			linePrefixSuffixRegex.append( '|' ); // we had a regex under construction so add on to it
		}

		// now the suffix
		lineSuffixRegex = ruleOption.getLineSuffixRegex();
		if ( lineSuffixRegex != null &&  lineSuffixRegex.length() > 0 ) {
			lineSuffixRegexExists = true; 

			try {
				linePrefixSuffixPattern = Pattern.compile( lineSuffixRegex );
			} catch (final PatternSyntaxException e) {
				System.err.println( "\nerror: RULE with id <" + ruleID + "> had option <customeSuffixRegex>, which did not compile. " + e.getMessage());
				System.exit( 15 );
			}

			linePrefixSuffixRegex.append( lineSuffixRegex );

			if ( lineSuffixRegex.charAt( lineSuffixRegex.length() -1 ) != '$' ) {
				linePrefixSuffixRegex.append("$");
			}

		}

		// final test of combined prefix/suffix ORed with anchors
		if ( linePrefixRegexExists || lineSuffixRegexExists ) {

			try {
				linePrefixSuffixPattern = Pattern.compile( linePrefixSuffixRegex.toString() );
			} catch (final PatternSyntaxException e) {
				System.err.println( "\nerror: RULE with id  <" + ruleID + "> had option <customePrefixRegex and or lineSuffixRegex>, which did not compile when ORed together. " + e.getMessage());
				System.exit( 15 );
			}
		}

		// handle the line check regex pattern, to be used later
		if ( (lineCheckRegex = ruleOption.getLineCheckRegex()) != null) {
			lineCheckRegexExists = true;

			try {
				lineCheckPattern = Pattern.compile( lineCheckRegex );
			} catch (final PatternSyntaxException e) {
				System.err.println("\nerror: RULE with  id <"
						+ ruleID
						+ "> had option <lineCheckRegex>, which did not compile. "
						+ e.getMessage());
				System.exit(15);
			}
		}

		/* prep work if the user provide a replacement string option, if so this will be 
		 * processed just AFTER the skipLine
		 */

		lineReplaceRegex = ruleOption.getLineReplaceRegex();
		if (  lineReplaceRegex != null && lineReplaceRegex.length() > 0 ) {
			// first split the value and see if format is ok
			lineReplaceFlag = true;

			replaceArray = lineReplaceRegex.split( ruleOption.getLineReplaceDelimiterRegex(), 2 );

			// replaceArray[0] = search_string
			// replaceArray[1] = replacement_string or null
			if ( replaceArray[0].length() == 0 ) {
				System.err.println("\nerror: search string of a <lineReplaceRegex> cannot be null in RULE with Id <" + ruleID + ">.");
			}

			// if there was no delimiter or the second parameter was null
			if ( replaceArray.length == 1 || replaceArray[1] == null ) {
				replaceRegex = "";
			} else {
				replaceRegex = replaceArray[1];
			}
		}

	} 

} // end of class
