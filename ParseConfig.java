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
 * ParseConfig - reads the -c config and checks syntax of the various blocks
 * 
 * Note: to the user a RULE block has an ID (-i id) in the code its ruleID
 */

package validate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import validate.Validate.format;

/**
 * ParseConfigFile - given the file associated with the "-c" switch
 * this object reads and processes the several block types within the file.
 * Most syntax errors are caught here and cause an exit and a message.
 * 
 * @author Bill Lanahan
 *
 */
class ParseConfig {
	// 0=have not seen, 1=have seen but NOT seen end yet, 2=had seen and now have seen the end
	private short haveOptionBlock = 0;
	private short haveMacroBlock = 0;
	private short haveRuleBlock = 0;
	private short haveRegexBlock = 0;

	private String fileName = null;
	private short lineNumber = 0;
	private String line; 
	private BufferedReader br;

	// we do the  patterns here so they can be reused without recompiling
	private final Pattern blankAndCommentPattern = Pattern.compile( "^\\s*#|^\\s*$" );
	private final Pattern optionPattern = Pattern.compile( "^\\s*(\\w+)(?:\\s*=\\s*)(.*)$" );
	private Pattern lineNumberPrefixPattern;
	 
	private Matcher m = null;
	private String[] tokens;
	private boolean haveFirstRule = false;
	private File inputFile;
	
	Options ruleOption = null;
	RegexBlock regexBlock = null;
	Rule tmpRule = null;

	//*********************************************************************************************************
	// we'll instantiate Options even if no option block as other blocks may
	// use the default data
	static Options opt = new Options();  
	// build Macro object for built-in and may be more
	static Macros macros = new Macros( Validate.macrosFile, opt.getMacroStartString(), opt.getMacroEndString() );
	//***********************************************************************************************************	
	
	ParseConfig() {
		System.out.println( "\nfatal: programming error ParseConfigFile needs a non-default constructor." );
		System.exit( 12 );
	}

	ParseConfig( String configFileName ) {
		setFileName( configFileName );
	}
	
	// the config file and pass off to other classes as needed
	// this method reads the entire rules file and creates/calls other objects as needed for the 
	// 3 block types option|macro|rule
	void parse() {

		try {
			lineNumberPrefixPattern = Pattern.compile( "^\\s*%(\\d+)%(\\d+)%(.*)$" );
		} catch ( PatternSyntaxException e ) {
			System.err.println( "\nerror: failed to compile lineNumberPrefixPattern, programming error \n" );
			System.exit(55);
		}
		
		try {
			inputFile = new File( fileName );
			br = new BufferedReader( new FileReader( inputFile ) );

			while ( true ) {

				line = br.readLine();
				
				if ( line == null ) {
					checkEOFstate( );
					return;
				} else {
					lineNumber++;
				}

				// need to swallow lines UNLESS we see a line that starts with "startBlock" (%%)
				if ( ! line.matches( "^\\s*" + Validate.blkStart + "\\s*\\w+.*$" ) ) {
					// swallow lines NOT within blocks
					continue;
				} else {
					// this MUST follow test for blockStart since they are similar 
					m = Validate.blkEndPattern.matcher( line );
					if ( m.find() ) {
						System.out.println( "\nfatal: blkEnd was found on line <" + lineNumber + "> before any block was begun." );
						System.exit( 12 );
					}
				}

				// ok now we need to see which block we are in
				line = line.trim().replaceFirst("^\\s*" + Validate.blkStart + "\\s*", "");
				tokens = line.split(  "\\s+" );
								
				// we know 0 is the blkStart string
				if ( (tokens.length > 0) && tokens[0].toUpperCase().matches("OPTIONS?")) {
					if ( haveOptionBlock != 0 ) {
						System.out.println( "\nfatal: a second OPTION block was found on line <" + lineNumber + ">." );
						System.exit( 12 );
					}

					if ( (haveMacroBlock > 0) || (haveRuleBlock > 0) ) {
						System.out.println( "\nfatal: the OPTION block on line <" + lineNumber + "> must preceed the MACRO and RULE blocks." );
						System.exit( 12 );
					}

					doParseOPTIONS();
				} else if ( (tokens.length == 1) && tokens[0].toUpperCase().matches("MACROS?") ) {
					doParseMACROS();
				} else if ( (tokens.length == 2) && tokens[0].toUpperCase().matches("RULES?") ) {
					doParseRULES( tokens[1] );
				} else {
					System.err.println( "\nfatal: invalid format on line <" + lineNumber + "> of config file <" + fileName + ">. line=" +line);
					System.exit( 12 );
				}

			}
			
		} catch (final IOException e) {
			System.err.println( "\nfatal: can't read config file <" + fileName + ">." );
			System.exit( 12 );
		} finally {
			br = null;
			inputFile = null;
		}
	}


	void setFileName ( String fileName ) {
		assert fileName != null;
		this.fileName = fileName;

	}

	void doParseOPTIONS() {
		haveOptionBlock = 1;


		// read until endBlk
		try {

			while ( true ) {
				line = br.readLine();
				if ( line == null ) {
					checkEOFstate();
					break;
				} else {
					lineNumber++;
				}

				// skip BLANKS and COMMENTS
				m = blankAndCommentPattern.matcher( line );
				if ( m.find() ) {
					continue;
				}

				// is it the endBlk - so we check the OPTIONS now that we are done
				m = Validate.blkEndPattern.matcher( line );
				if ( m.find() ) {
					doOptionsCheck();
					haveOptionBlock = 2;
					return;
				}

				// here we need check and set each variable
				// we'll reduce and tokenize each line and send it to the OPTIONS class for processing
				m = optionPattern.matcher( line );

				if ( m.find() ) {
					opt.optNameValue( m.group( 1 ), m.group( 2 ), lineNumber );
					continue;
				} else {
					if ( opt.getWarnInvalidOption() ) {
						System.out.println( "\nwarning: invalid OPTION format on line <" + lineNumber + ">" );
					}
					continue;		
				}

			}
		} catch ( final IOException e ) {
			System.err.println( "\nfatal: problem reading config file <" + fileName + "> " + e.getMessage() );
			System.exit( 12 );
		}

	}

	/*
	 * options have all been read - so their format was checked but they
	 * may have conflicts with other options - this will be checked here
	 */
	void doOptionsCheck() {
		// need to call any time options have been changed
		// we have some inconsistent values that could not be tested on the fly
		if ( opt.getMacroStartString().equals( opt.getMacroEndString() ) ) {
			System.err.println( "\nerror: OPTION <macroStartString> cannot be the same as <macroEndString>." );
			System.exit( 12 );
		}

		if ( opt.getRegexEndBlockRegex().equals( opt.getCommentRegex() ) ) {
			System.err.println( "\nerror: OPTION <regexEndBlockRegex> cannot be the same as <regexCommentExpression>." );
			System.exit( 12 );
		}

		if ( Validate.blkEnd.equals( opt.getCommentRegex() ) ) {
			System.err.println( "\nfatal: OPTION <commentRegex> cannot be the same as command line option <blkEnd>." );
			System.exit( 12 );
		}

		if ( Validate.blkStart.equals( opt.getCommentRegex() ) ) {
			System.err.println( "\nfatal: OPTION <commenrRegex> cannot be the same as command line option <blkStart>." );
			System.exit( 12 );
		}

		if ( Validate.blkEnd.equals( opt.getCommentRegex() ) ) {
			System.err.println( "\nfatal: OPTION <commentRegex> cannot be the same as command line option <blkEnd>." );
			System.exit( 12 );
		}

	}

	/* 
	 * reads an internal macro block or macro file
	 */
	void doParseMACROS() {
		haveMacroBlock = 1;

		// read until endBlk
		try {
			String tmpline = null;
			boolean continuationState = false;
			short startLine = 0; //need to save the line that a multiline started on
			boolean startedContinuation = false;
			
			while ( true ) {
				line = br.readLine();
				
				if ( line == null ) {
					checkEOFstate();
					break;
				} else {
					lineNumber++;
				}

				// skip BLANKS and COMMENTS
				m = blankAndCommentPattern.matcher( line );
				if ( m.find() ) {
					continue;
				}

				// is it the endBlk - so we check the OPTIONS now that we are done
				m = Validate.blkEndPattern.matcher( line );
				if ( m.find() ) {
					haveMacroBlock = 2;
					return;
				}

				// the Macros class will check line formatting
				if ( line.endsWith("\\") && ! line.endsWith("\\\\")  ) {

					if ( startedContinuation == false ) {
						startedContinuation = true;
						startLine = lineNumber;
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

				if ( tmpline == null ) {
					Macros.addSingleMacro( line, lineNumber, opt ); 
				} else {
					Macros.addSingleMacro( tmpline, startLine, opt );
					tmpline = null;
				}

				continuationState = false; // reset

			}
		} catch ( final IOException e ) {
			System.err.println( "\nfatal: problem reading config file <" + fileName + "> " + e.getMessage() );
			System.exit( 12 );
		}

	}

	/*
	 * %% RULES has been seen
	 * this method deals will all lines AFTER the title line
	 * doParseRULES - since a %% RULES title was encountered, we are now in a RULES block
	 * we read this block until the blkEnd (default %%) line. This block can set/reset a few OPTIONS; it
	 * has an ID for association with the  command line ID and may have a list of files it is meant to parse.
	 * @param String ruleID is a \\w string
	 */
	void doParseRULES( String ruleID ) {
		haveRuleBlock = 1;
		final Pattern nameValuePattern = Pattern.compile( "^NameValue(?:Pair)?$|^NVP?$", Pattern.CASE_INSENSITIVE );
		final Pattern delimitedValuePattern = Pattern.compile( "^Delimited$|^Fields?$|^[DF]$", Pattern.CASE_INSENSITIVE );
		final Pattern javaValuePattern = Pattern.compile( "^Java(?:Properties)?$|^JP?$", Pattern.CASE_INSENSITIVE );
		final Pattern customValuePattern = Pattern.compile( "^Custom$|^C$", Pattern.CASE_INSENSITIVE );
		final Pattern lineValuePattern = Pattern.compile( "^Lines?$|^L$", Pattern.CASE_INSENSITIVE );
		final Pattern lineValueCmdLinePattern = Pattern.compile( "^ValueLines?$|^VL$", Pattern.CASE_INSENSITIVE );
		final Pattern delimitedValueCmdLinePattern = Pattern.compile( "^ValueDelimited$|^ValueFields$|^V[DF]$", Pattern.CASE_INSENSITIVE );


		tmpRule = null;
		Matcher m;

		if ( haveFirstRule == false ) {
			haveFirstRule = true;
			
			// special case if we have -M switch and have now read macros we can display
			if ( Validate.showMacros ) {
				Macros.showMacros();
				System.exit( 0 );
			}
					
			Validate.ruleMap = new HashMap<String, Rule>(); // will save all Rule objects for run time
			Validate.ruleList = new ArrayList<String>();
		}

		// check if id is in Map already
		if ( ! Validate.ruleMap.containsKey( ruleID ) ) {
			// then its not a previously "seen" ruleID so save it IF the format is good
			
			// make sure ruleID is valid and non-existent
			if ( ! ruleID.matches( "[a-zA-Z_0-9\\./\\$]+" ) ) {
				System.err.println( "\nfatal: invalid format for the ID for the RULE block on line <" + lineNumber + "> of config file <" + fileName + ">.");
				System.exit( 12 );
			}

			// later we will CLONE the OPTIONS object so that each RULE block can have its
			// own private copy of OPTIONS
			// clone the Options class and then we will over write options for
			// local use by this Rule block
			try {
				ruleOption = (Options) opt.clone();
			} catch ( final CloneNotSupportedException ex ) {
				System.err.println( "Cloneable should be implemented." );
				System.exit( 12 );
			}
			
			// this comes late because we had to finish OPTION block and only print it once.
			// it DOES mean that warning may come out prior to the tool title
			//
			
			if ( Validate.ruleMap.isEmpty() && ruleOption.getShowToolTitle() == true ) {
				System.out.println("\n\n\t\t\t\tValidate\n\t\t\t\t========\n\n");
			}
			
			// start the real RULE block parsing
			// create the RULE object with its own OPTIONS
			tmpRule = new Rule( ruleID, ruleOption, fileName ); 
			
			if ( tmpRule != null ) {
				Validate.ruleMap.put( ruleID, tmpRule );
				Validate.ruleList.add( ruleID ); // so we can execute in order
			} else {
				System.out.println( "\nfatal: the ruleID <" + ruleID + "> on line <" + lineNumber + "> could not be placed in ruleMap " );
				System.exit( 12 );	
			}

		} else {
			System.out.println( "\nfatal: the ruleID <" + ruleID + "> on line <" + lineNumber + "> is a duplicate. " );
			System.exit( 12 );
		}


		// read until endBlk
		// so options in the form of name=value found from here on go to the
		// new CLONED object, for this RULE block only

		try {
			while ( true ) {
				line = br.readLine();
				
				if ( line == null ) {
					// lots of thing to check here 
					checkEOFstate();
					break;
				} else {
					lineNumber++;
				}

				// need to swallow lines UNLESS we see a line that starts with "endBlock" (%%)

				// test for end or RULE or REGEX anything else that starts with endBLock is an error
				m = Validate.blkEndPattern.matcher( line );
				if ( m.find() ) {
					// see if its a block end or a REGEX block start
					 
					
					line = line.trim().replaceFirst("^" + Validate.blkStart + "\\s*", "");
					// all that is left is the TITLE + trailing junk (maybe)
					
					tokens = line.split( "\\s+" );
										
					// we know 0 is the blkStart string
					  if ( tokens.length == 1 &&  tokens[0].toUpperCase().matches("REGEX(ES)?")) {
						 if ( haveRegexBlock != 0 ) {
							System.out.println( "\nfatal: a second REGEX block was found on line <" + lineNumber + "> for this RULE block." );
							System.exit( 12 );
						} 

						// this RULE block has a REGEX, so we need to parse it
						parseRegexBlock( ruleOption, ruleID, lineNumber );
					} else if ( line.length() == 0 ) {
						// we reached the end
						haveRuleBlock = 2;
					} else {
						System.err.println( "\nfatal: unknown/expected format line on line <" + lineNumber + ">.");
						System.exit( 12 );
					}

					// endBlk we are done here
					// if there was a REGEX block, it was already parsed	
					return;
				}
				
				// skip BLANKS or COMMENT lines
				m = blankAndCommentPattern.matcher( line );
				if ( m.find() ) {
					continue;
				}

				// here we need check and set each variable
				// need to check for options, files, type, regex block etc

				/* what might we find in RULES?
				 * 1- options, as name=value
				 * 2- specific options/instructions for RULES (files, type)
				 * 3- a REGEX block start
				 * 4- and END block "\\s*%%
				 */

				// First we see if we have a RULE specific OPTION
				// here we need check and set each variable
				// we'll reduce and tokenize each line and send it to the OPTIONS class for processing
				m = optionPattern.matcher( line );
				if ( m.find() ) {
					final String ruleOpt = m.group( 1 );
					String ruleValue = m.group( 2 );

					//
					// FORMAT OPTION
					//
					if ( ruleOpt.equals( "format" ) )  {

						ruleValue = Options.deQuote( ruleValue );
						
						m = nameValuePattern.matcher( ruleValue );
						if ( m.find() ) {
							tmpRule.setFormat( format.NAME_VALUE );
							continue;
						}

						m = customValuePattern.matcher( ruleValue );
						if ( m.find() ) {
							tmpRule.setFormat( format.CUSTOM );
							continue;
						}

						m = delimitedValuePattern.matcher( ruleValue );
						if ( m.find() ) {
							tmpRule.setFormat( format.DELIMITED );
							continue;
						}

						m = javaValuePattern.matcher( ruleValue );
						if ( m.find() ) {
							tmpRule.setFormat( format.JAVA );
							continue;
						}

						m = lineValuePattern.matcher( ruleValue );
						if ( m.find() ) {
							tmpRule.setFormat( format.LINE );
							continue;
						}
						
						m = lineValueCmdLinePattern.matcher( ruleValue );
						if ( m.find() ) {
							tmpRule.setFormat( format.VALUE_LINE );
							continue;
						}
						
						m = delimitedValueCmdLinePattern.matcher( ruleValue );
						if ( m.find() ) {
							tmpRule.setFormat( format.VALUE_DELIMITED );
							continue;
						}

						System.err.println( "\nfatal: unknown value <" + ruleValue + "> for <format> option on line <" + lineNumber + ">." );
						System.exit( 12 );
						
					} else if ( ruleOpt.equals( "file" ) ) {
													
						//
						// FILE OPTION
						//
						
						// 2 approaches for file: file=xxx, or file={\nxxx\nyyy}\n
						//
														
						 if ( ruleValue.matches( "\\{$" ) ) {
				              // we assume this is the start of a block
				              ///tmpRule.fileAdd( ruleValue );

				              while ( true ) {
				            	  				            
				                line = br.readLine();
				                
				                if ( line == null ) {
				                  // lots of thing to check here
				                  checkEOFstate();
				                  break;
				                }

				                // skip comments within the file=definition
				                m = blankAndCommentPattern.matcher( line );
				                if ( m.find() ) {
				                  lineNumber++;
				                  continue;
				                }

				                // what's left we expect is a file name or the end of the block
				                if ( line.matches( "^\\s*}\\s*$" )) {
				                  // end of block, swallow the end marker
				                  lineNumber++;
				                  break;
				                } else {
				                	// its a file we think - note since we already ruled out null, any character is valid in linux
				                	// we ignore the file option in the RULES if we got it on comadn line
									if ( ! Validate.fileIsCmdLine) {
										tmpRule.fileAdd( line.trim() ); 
								}
				                	
				                	lineNumber++;
				                	continue;
				                }
				              } // end file reading while
						 } else {  
							// its a file we think - note since we already ruled out null, any character is valid in linux
			                // we ignore the file option in the RULES if we got it on comadn line
							if ( ! Validate.fileIsCmdLine) {
					            // its a single file  
					            // since linux will take any char except null and slash we don't need the old test any more
								 ruleValue = Options.deQuote( ruleValue );
								 tmpRule.fileAdd( ruleValue.trim() );
							}
							 continue;
						 }
						 
						 continue; // "file" done read more of config file
					} else { 
						// looks like an option format so send it to Options class for evaluation
						ruleOption.optNameValue( ruleOpt, ruleValue, lineNumber );
						continue;
					}

				} // end of option checking

				if ( line.matches("^\\s*" + Validate.blkStart + "\\s*\\w+.*") ) {
					// possibly REGEX lets check
					line = line.trim().replaceFirst("^" + Validate.blkStart + "\\s*", "");
					tokens = line.split(  "\\s+" );

					// we know 0 is the blkStart string
					if ( (tokens.length == 1) && tokens[0].toUpperCase().matches("REGEX(ES)?") ) {
						if ( haveRegexBlock != 0 ) {
							System.out.println( "\nfatal: a second REGEX block was found on line <" + lineNumber + ">." );
							System.exit( 12 );
						} else {
							
							if ( tmpRule.getFormat() == null ) {
								System.err.println( "\nerror: RULE block that ends on line <" + lineNumber + "> requires format=<option>.");
								System.exit( 12 );
							}
							// we can now parse the regex line that are NOT comments
							lineNumber = parseRegexBlock( ruleOption, ruleID, lineNumber );
							return; // reached the end of REGEX and therefore RULE
						}
					} else {
						System.err.println( "\nfatal: invalid block start line <" + line + "> in RULE block, line <" + lineNumber + ">.");
						System.exit( 12 );
					}

				} else {
					System.err.println( "\nfatal: invalid line <" + line.trim() + "> in RULE block, line <" + lineNumber + ">.");
					System.exit( 12 );
				}

			} // while end
		}  catch (final IOException e) {
			System.err.println( "\nfatal: problem reading config file: " + e.getMessage() );
			System.exit( 12 );
		}
		

	} // end reads RULES block

	//////////////////////////////////////////////////////////////////////////////////
	////// REGEX BLOCK
	/////////////////////////////////////////////////////////////////////////////////
	/*
	 * we hit a REGEX title for this RULE block
	 */
	short parseRegexBlock( Options ruleOption, String ruleID, short lineNumber	) {
		boolean haveRegexCommentExpression = false;
		Pattern regexCommentPattern = null;
		Pattern regexEndBlockPattern = null;
		regexBlock = new RegexBlock( tmpRule, tmpRule.ruleFormat, ruleID, ruleOption.getDelimiterRegex(), ruleOption ); // scope only as long as parsing is done

		// get the regexCommentExpression and regexEndBlockRegex
		Matcher lnpMatcher = null;
		
		try {
				
			if ( ruleOption.getCommentRegex() != null ) {
				regexCommentPattern = Pattern.compile( ruleOption.getCommentRegex() );
				haveRegexCommentExpression = true;
			}

			if ( ruleOption.getRegexEndBlockRegex() != null ) {
				regexEndBlockPattern = Pattern.compile( ruleOption.getRegexEndBlockRegex() );
			} else {
				regexEndBlockPattern = Pattern.compile( Validate.blkEnd );
			}
			
			// 2 var * 2 combination for line continuation
			boolean continuationState = false;
			String tmpline = null;
			int fromLine = -1;
			int toLine = -1;
						
			while ( true ) {
								
				line = br.readLine();
				++lineNumber;
				
				if ( line == null ) {
					// lots of things to check here 
					checkEOFstate();
					break;
				} 
					
				// we only want this so we call the correct method in RegexBlock later
				if (  tmpRule.getFormat() == format.LINE ) {
					fromLine = toLine = 0;


					// following just gets line number ranges IF they are given
					lnpMatcher = lineNumberPrefixPattern.matcher( line );

					if ( lnpMatcher.find() ) {

						try {

							fromLine = Integer.parseInt( lnpMatcher.group(1) );

							toLine = Integer.parseInt( lnpMatcher.group(2) );

						} catch ( NumberFormatException e ) {
							System.err.println("\nerror, fromLine and toLine must be integers >= zero on line <" + lineNumber + ">");
							System.exit(77);
						}

						line = lnpMatcher.group(3);

						// need this because above does NOT catch negatives
						if ( fromLine < 0 || toLine < 0 ) {
							System.err.println("\nerror, fromLine and toLine must be integers >= zero on line <" + lineNumber + ">");
							System.exit(77);
						}

						if ( toLine != 0 && toLine < fromLine ) {
							System.err.println("\nerror, toLine must be greater >= fromLine unless toLine == 0 <" + lineNumber + ">");
							System.exit(77);
						}
					}
				}
					
				if ( ruleOption.getRegexLineContinuation() ) {
					
					if ( line.endsWith("\\") && ! line.endsWith("\\\\")  ) {
						
						// its a continuation; need to adjust line and concatenate
						if ( continuationState == false ) {
							// use line as is
							// swallow the \
							continuationState = true;
							tmpline = line.substring(0, line.length() - 1 ); // trims the backslash
							continue;
						} else {
							// got the final line
							line = line.trim(); // swallows the start of line WS
							line = line.substring(0, line.length() - 1); // trims the backslash
							tmpline += line;//
							continue; // read more
						}
						
					} else {
						
						if ( continuationState ) {
							// user wanted this feature, but input doesn't not qualify
							line = line.trim();
							tmpline += line;
							continuationState = false; // reset
							line = tmpline;
						}
				
					}
					
					continuationState = false; // reset
				}
				
				// we are in REGEX it has a special COMMENT string to test for
				if ( haveRegexCommentExpression ) {

					m = regexCommentPattern.matcher( line );

					if ( m.find() ) {
						continue;
					}
				}

				// make sure we didn't reach the end of the block
				m = regexEndBlockPattern.matcher( line );

				if ( m.find() ) {
					haveRuleBlock = 2;
					regexBlock.finalRegexStaticCheck( tmpRule, lineNumber );
					regexBlock = null; // all data now in RULE or OPTIONS
					checkEOFstate();
					
					break;
				}
				
				// so its a regex line - we need to handle differently based on format type
				// so we'll pass the line to REGEX and let it figure it out
				if ( fromLine != -1 ) {
					regexBlock.addRegexLine( line, lineNumber, ruleOption, fromLine, toLine );
				} else {
					regexBlock.addRegexLine( line, lineNumber, ruleOption, 0, 0 );
				}
							
			}
			
		} catch (final IOException e) {
			System.err.println( "\nfatal: problem reading config file <" + fileName + ">: " + e.getMessage() );
			System.exit( 12 );
		} catch ( final PatternSyntaxException pse ) {
			System.err.println( "\nfatal: regex compile issue line <" + lineNumber + "> in config file <" + fileName +  "> "  + pse.getMessage() );
			System.exit( 12 );
		}
		
		
		return lineNumber;
	
		
	} // end of parseRegexBlock

	/*
	 * checkEOFstate - this method is called when we hit EOF in the config file
	 * In this case we need to see if any block construct was properly ended vs
	 * we just fell off the end. 
	 */
	void checkEOFstate( ) {

		boolean exitFlag = false;
		
			
		// we were reading the config file and its the end, need to see if the syntax was all done
		if( haveOptionBlock == 1 ) {
			exitFlag = true;
			System.err.println( "\nfatal: the OPTION block was never ended in config file <" + fileName + ">." );
		}

		if( haveMacroBlock == 1 ) {
			exitFlag = true;
			System.err.println( "\nfatal: the MACRO block was never ended in config file <" + fileName + ">." );
		}

		if( haveRuleBlock == 1 ) {
			exitFlag = true;
			System.out.println( "\nfatal: the last RULE block was never ended in config file <" + fileName + ">." );
		} else if ( haveRuleBlock == 0 ) {
			exitFlag = true;
			System.err.println( "\nfatal: at least one RULE block is required in config file <" + fileName + ">." );
		}

		if ( exitFlag ) {
			System.exit( 12 );
		}
	
		
		return;
	
	} // end checkEOFstate

	
}



