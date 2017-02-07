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

import java.io.File;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;


/**
 * class to hold the default options, save changes to user modified options, and parse 
 * the options block
 * @author Bill Lanahan
 *
 */

class Options implements Cloneable {

	private final Pattern truePattern = Pattern.compile( "^(?:\"?)(TRUE|T|YES|Y|1|ON)(?:\"?)$", Pattern.CASE_INSENSITIVE );
	private final Pattern falsePattern = Pattern.compile( "^(?:\"?)(FALSE|F|NO|N|0|OFF)(?:\"?)$", Pattern.CASE_INSENSITIVE );
	private final Pattern optionsWithNullValueAllowed = Pattern.compile( "lineSkipRegex|linePrefixRegex|Regex|lineCheckRegex|dir(ectory)?PathString", Pattern.CASE_INSENSITIVE );
	private Matcher m = null;
	
	private boolean errorFieldUnderline;
	private boolean validLineUnderline;
	private boolean useMacros;
	private boolean useLineRangeRestrictions;
	private String regexCommentRegex;
	private boolean regexLineContinuation;
	private boolean fileLineContinuation;
	private String delimiterRegex;
	private String macroStartString;
	private String macroEndString;	
	private static boolean macroContinuation;
	private String regexEndBlockRegex;
	private String dirPathString;
	private boolean showValidData;
	private boolean showToolTitle;
	private String EOLdelimiter;
	private short extraFieldCount;
	private char	errorReportDetails;
	private char	errorReportSummary;
	private String lineSkipRegex;
	private String lineCheckRegex;
	private boolean lineShow;
	private String linePrefixRegex;
	private String lineSuffixRegex;
	private String lineReplaceRegex;
	private String lineReplaceDelimiterRegex;
	
	private boolean warnDuplicates;
	private boolean warnExtraFields;
	private boolean warnUncheckedName;
	private boolean warnRegexUnused;
	private boolean warnInvalidOption;
	private boolean warnRuleWithoutFile;
	private boolean warnMacroOverRide;
		
	Options() {

		// set defaults but can be over written by user
		warnInvalidOption = true;
		warnRuleWithoutFile = true;
		errorFieldUnderline = true; 
		validLineUnderline = false;
		useMacros = false;
		useLineRangeRestrictions = true;
		warnMacroOverRide = false;
		// final checks on strings needed to prevent logical collisions
		regexCommentRegex = "^\\s*#";
		delimiterRegex = "=";
		macroStartString = "<";
		macroEndString = ">";
		macroContinuation = true;
		regexEndBlockRegex = "^\\s*%%";
		regexLineContinuation = false;
		dirPathString = Validate.dirPath;
		errorReportDetails = 'e'; // e=empty, n=numberLine, l=line, a=all
		errorReportSummary = 'a'; // a=always|yes, f=failure
		fileLineContinuation = false;
		showValidData = false;
		showToolTitle = true;
		EOLdelimiter = "optional";
		extraFieldCount = 0;
		warnDuplicates = true;
		warnUncheckedName = false;
		warnExtraFields = false;
		warnRegexUnused = true;
		lineSkipRegex = "^\\s*#|^\\s*$";
		lineCheckRegex = null;
		lineShow = false;
		linePrefixRegex = null;
		lineSuffixRegex = null;
		lineReplaceDelimiterRegex = "/";
		lineReplaceRegex = null;
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
	
	/* following are setters and getters of the options. They can override default or in the case of
	 * a rules block modify that value for just the block of code.
	 */
	public boolean getWarnInvalidOption() {
		return this.warnInvalidOption;
	}

	public void setWarnInvalidOption( boolean warnInvalidOption ) {
		this.warnInvalidOption = warnInvalidOption;
	}
	
	public boolean getShowValidData() {
		return this.showValidData;
	}
	
	public void setShowValidData( boolean showValidData ) {
		this.showValidData = showValidData;
	}
	
	public boolean getLineShow() {
		return this.lineShow;		
	}
	
	public void setLineShow( boolean lineShow ) {
		this.lineShow = lineShow;
	}

	public boolean getErrorFieldUnderline() {
		return this.errorFieldUnderline;
	}

	public void setErrorFieldUnderline( boolean errorFieldUnderline ) {
		this.errorFieldUnderline = errorFieldUnderline;
	}
	
	public void setValidLineUnderline( boolean validLineUnderline ) {
		this.validLineUnderline = validLineUnderline;
	}
	
	public boolean getValidLineUnderline() {
		return this.validLineUnderline;
	}

	public boolean getUseMacros() {
		return this.useMacros;
	}

	public void setUseMacros( boolean useMacros ) {
		this.useMacros = useMacros;
	}
	
	public boolean getUseLineRangeRestrictions() {
		return this.useLineRangeRestrictions;
	}

	public void setUseLineRangeRestrictions( boolean useLineRangeRestrictions ) {
		this.useLineRangeRestrictions = useLineRangeRestrictions;
	}
	
	public boolean getShowToolTitle() {
		return this.showToolTitle;
	}
	
	public void setShowToolTitle( boolean showToolTitle ) {
		this.showToolTitle = showToolTitle;
	}

	public String getMacroStartString() {
		return macroStartString;
	}

	public void setMacroStartString( String macroStartString ) {
		this.macroStartString = macroStartString;
	}

	public String getMacroEndString() {
		return macroEndString;
	}

	public void setMacroEndString( String macroEndString ) {
		this.macroEndString = macroEndString;
	}

	public boolean getWarnMacroOverRide() {
		return warnMacroOverRide;
	}

	public void setWarnMacroOverRide( boolean warnMacroOverRide ) {
		this.warnMacroOverRide = warnMacroOverRide;
	}

	public String getCommentRegex() {
		return regexCommentRegex;
	}

	public void setCommentRegex( String regexCommentRegex ) {
		this.regexCommentRegex = regexCommentRegex;
	}

	public String getRegexEndBlockRegex() {
		return regexEndBlockRegex;
	}

	public void setRegexEndBlockRegex( String regexEndBlockRegex ) {
		this.regexEndBlockRegex = regexEndBlockRegex;
	}

	public void setWarnRuleWithoutFile( boolean warnRuleWithoutFile ) {
		this.warnRuleWithoutFile = warnRuleWithoutFile;
	}

	public boolean getWarnRuleWithoutFile( ) {
		return this.warnRuleWithoutFile;
	}
	
	public void setWarnExtraFields( boolean warnExtraFields ) {
		this.warnExtraFields = warnExtraFields;
	}

	public boolean getWarnExtraFields( ) {
		return this.warnExtraFields;
	}
	
	public String getDirPathString() {
		return dirPathString;
	}

	public void setDirPathString( String dirPathString ) {
		// we do NOT set if it was set on command line
		if ( Validate.dirPath != null ) {
			return;
		}
		this.dirPathString = dirPathString;
	}
	
	public char getErrorReportDetails() {
		return errorReportDetails;
	}

	public void setErrorReportDetails( char errorReportDetails ) {
		this.errorReportDetails = errorReportDetails;
	}
	
	public char getErrorReportSummary() {
		return errorReportSummary;
	}
	
	public void setErrorReportSummary( char errorReportSummary ) {
		this.errorReportSummary = errorReportSummary;
	}

	public void setSelimiterString( String delimiterRegex ) {
		this.delimiterRegex = delimiterRegex;
	}
	
	public String getDelimiterRegex( ) {
		/*
		 * used to skip any lines in a DELIMITED file that are non-delimited
		 */
		return delimiterRegex;
	}
		
	public String getLineSkipRegex() {
		return lineSkipRegex;
	}
	
	public void setLineSkipRegex( String lineSkipRegex ) {
		this.lineSkipRegex = lineSkipRegex;
	}
	
	public String getLineCheckRegex() {
		return lineCheckRegex;
	}
	
	public void setLineCheckRegex( String lineCheckRegex ) {
		this.lineCheckRegex = lineCheckRegex;
	}

	public void setEOLdelimiter( String EOLdelimiter ) {
		this.EOLdelimiter = EOLdelimiter;
	} 
	
	public String getEOLdelimiter() {
		return EOLdelimiter;
	} 
	
	public boolean getWarnDuplicates() {
		return this.warnDuplicates;
	}
	
	public void setWarnDuplicates( boolean warnDuplicates ) {
		this.warnDuplicates = warnDuplicates;
	}

	public boolean getWarnUncheckedName() {
		return this.warnUncheckedName;
	}
	
	public void setWarnUncheckedName( boolean warnUncheckedName ) {
		this.warnUncheckedName = warnUncheckedName;
	}
	
	public void setWarnRegexUnused( boolean warnRegexUnused ) {
		this.warnRegexUnused = warnRegexUnused;
	}
	
	public boolean getWarnRegexUnused() {
		return this.warnRegexUnused;
	}
	
	public void setRegexLineContinuation( boolean regexLineContinuation ) {
		this.regexLineContinuation = regexLineContinuation;
	}
	
	public boolean getRegexLineContinuation() {
		return regexLineContinuation;
	}
	
	public void setFileLineContinuation( boolean fileLineContinuation ) {
		this.fileLineContinuation = fileLineContinuation;
	}
	
	public boolean getFileLineContinuation() {
		return fileLineContinuation;
	}
	
	public void setDelimiterRegex( String delimiterRegex ) {
		this.delimiterRegex = delimiterRegex;
	}
	
	public void setLinePrefixRegex( String linePrefixRegex ) {
		this.linePrefixRegex = linePrefixRegex;
	}
	
	public String getLinePrefixRegex() {
		return linePrefixRegex;
	}
	
	public void setLineSuffixRegex( String Regex ) {
		this.lineSuffixRegex = Regex;
	}
	
	public String getLineSuffixRegex() {
		return lineSuffixRegex;
	}
	
	public String getLineReplaceDelimiterRegex() {
		return lineReplaceDelimiterRegex;
	}
	
	public void setLineReplaceDelimiterRegex( String lineReplaceDelimiterRegex ) {
		this.lineReplaceDelimiterRegex = lineReplaceDelimiterRegex;
	}
	
	public String getLineReplaceRegex() {
		return lineReplaceRegex;
	}
	
	public void setLineReplaceRegex( String lineReplaceRegex ) {
		this.lineReplaceRegex = lineReplaceRegex;
	}
	
	public void setExtraFieldCount( short extraFieldCount ) {
		this.extraFieldCount = extraFieldCount;
	}
	
	public short getExtraFieldCount()  {
		return this.extraFieldCount;
	}
	
	public static boolean getMacroContinuation() {
		return Options.macroContinuation;
	}
	
	public void setMacroContinuation( boolean macroContinuation) {
		Options.macroContinuation = macroContinuation;
	}
	
	/*
	 * optNameValue - takes the name, value and the line number where they occurred in the config file
	 * and tests to see first - if the name is a real option name, second if the value is acceptable
	 * and if so saves either what was passed in OR a least common representation of it.
	 * Unrecognized name is a warning (probably and error to the user)
	 */
	short optNameValue ( String name, String value, short lineNumber ) {
		
		//System.err.println("\nDEBUG: name <" + name + "> value: <" + value + "> line <"  + lineNumber + ">");
		
		// only a few options can have null value, test here
		m = optionsWithNullValueAllowed.matcher( name );
					
		if ( m.find() && value == null ) {
			System.err.println( "\nwarning: option <" + name + "> cannot have a null value, on line <" + lineNumber + ">.");
							System.exit( 55 );
		}
		 		
		//
		// warnInvalidOption
		//
		if ( name.matches( "(?i)warn(ing)?InvalidOption" ) ) { 
			value = deQuote( value );

			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setWarnInvalidOption( true );
				return lineNumber;
			}

			m = falsePattern.matcher ( value );
			if ( m.find() ) {
				setWarnInvalidOption( false );
				return lineNumber;
			} 

			// else 
			m = null;
			
			System.out.println( "\nwarning: OPTION <warnInvalidOption> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
			return lineNumber;
			


		} else 	if ( name.matches( "(?i)warn(ing)?RegexUnused" ) ) { 
			//
			// warnRegexUnused
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setWarnRegexUnused ( true );
				return lineNumber;
			}

			m = falsePattern.matcher ( value );
			if ( m.find() ) {
				setWarnRegexUnused( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <warnRegexUnused> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
				
		} else 	if ( name.matches( "(?i)warn(ing)?RuleWithoutFile" ) ) { 
			//
			// warnRuleWithoutFile
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setWarnRuleWithoutFile ( true );
				return lineNumber;
			}

			m = falsePattern.matcher ( value );
			if ( m.find() ) {
				setWarnRegexUnused( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <warnRuleWithoutFile> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else 	if ( name.matches( "(?i)lineShow" ) ) { 
			//
			// lineShow
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setLineShow ( true );
				return lineNumber;
			}

			m = falsePattern.matcher ( value );
			if ( m.find() ) {
				setLineShow( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <lineShow> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)warn(ing)?Duplicates" ) ) { 
			//
			// warnDuplicateName
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setWarnDuplicates( true );
				return lineNumber;
			}

			m = falsePattern.matcher ( value );
			if ( m.find() ) {
				setWarnDuplicates( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <warnDuplicateName> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)useLineRangeRestrictions?" ) ) { 
			//
			// useLineRangeRestrictions
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setUseLineRangeRestrictions( true );
				return lineNumber;
			}

			m = falsePattern.matcher ( value );
			if ( m.find() ) {
				setUseLineRangeRestrictions( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <useLineRangeRestrictions> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}	
		} else if ( name.matches( "(?i)warn(ing)?ExtraFields" ) ) { 
			//
			// warnExtraFields
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setWarnExtraFields( true );
				return lineNumber;
			}

			m = falsePattern.matcher ( value );
			if ( m.find() ) {
				setWarnExtraFields( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <warnExtraFields> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}	
			
		
		} else if ( name.matches( "(?i)warn(ing)?UncheckedName" ) ) { 
			//
			// warnUncheckedName
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setWarnUncheckedName( true );
				return lineNumber;
			}

			m = falsePattern.matcher ( value );
			if ( m.find() ) {
				setWarnUncheckedName( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnUncheckedName ) {
				System.out.println( "\nwarning: OPTION <warnUncheckedName> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
		
		} else if ( name.matches( "(?i)errorFieldUnderline" ) ) {
			//
			// errorFieldUnderline
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setErrorFieldUnderline( true );
				return lineNumber;
			} 

			m = falsePattern.matcher ( value );
			if ( m.find()  ) {
				setErrorFieldUnderline( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <errorFieldUnderline> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)validLineUnderline" ) ) {
			//
			// validLineUnderline
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setValidLineUnderline( true );
				return lineNumber;
			} 

			m = falsePattern.matcher ( value );
			if ( m.find() ) {
				setValidLineUnderline( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <validLineUnderline> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)useMacros?" ) ) {
			//
			// useMacros
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setUseMacros( true );
				return lineNumber;
			} 

			m = falsePattern.matcher ( value );
			if ( m.find()  ) {
				setUseMacros( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <useMacrosBlock> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)warn(ing)?MacroOverRide" ) ) {
			//
			// warnMacroOverRide
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setWarnMacroOverRide( true );
				return lineNumber;
			} 

			m = falsePattern.matcher ( value );
			if ( m.find()  ) {
				setWarnMacroOverRide( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <warnMacroOverRide> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)macroStartString" ) ) {
			//
			// macroStartString
			//
			value = deQuote( value );
			
			this.macroStartString = value;
			return lineNumber;
						
		} else if ( name.matches( "(?i)macroEndString" ) ) {
			//
			// macroEndString
			//
			value = deQuote( value );
			
			if ( value == null ) {
				if ( this.warnInvalidOption ) {
					System.out.println( "\nwarning: OPTION <macroEndString> cannot be null, on line <" + lineNumber + ">." );
				}
				System.exit( 11 );
			} else {
				this.macroEndString = value;
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)EOLdelimiter" ) ) {
			//
			// EOLdelimiter
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setEOLdelimiter( "true" );
				return lineNumber;
			} 

			m = falsePattern.matcher ( value );
			if ( m.find()  ) {
				setEOLdelimiter( "false" );
				return lineNumber;
			} 
			
			m = null;
			
			if ( value.matches( "(?i)optional" )  ) {
				setEOLdelimiter( "optional" );
				return lineNumber;
			} else { 
				System.out.println( "\nwarning: OPTION <" + name + "> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)extraFieldCount" ) ) {
			//
			// extraFieldCount
			//
			
			value = deQuote( value );
			try {
				setExtraFieldCount( Short.parseShort( value ) );
			} catch( NumberFormatException e ) {
				System.out.println( "\nerror: OPTION <extraFieldCount> value on line <" + lineNumber + "> must be -1, 0, 1..." );
				System.exit(99);
			}
						
		} else if ( name.matches( "(?i)regexLineContinuation" ) ) {
			//
			// regexLineContinuation
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setRegexLineContinuation( true );
				return lineNumber;
			} 

			m = falsePattern.matcher ( value );
			if ( m.find()  ) {
				setRegexLineContinuation( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <regexLineContinuation> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)macroContinuation" ) ) {
			//
			// macroContinuation
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setMacroContinuation( true );
				return lineNumber;
			} 

			m = falsePattern.matcher ( value );
			if ( m.find()  ) {
				setMacroContinuation( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <macroContinuation> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)fileLineContinuation" ) ) {
			//
			// fileLineContinuation
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setFileLineContinuation( true );
				return lineNumber;
			} 

			m = falsePattern.matcher ( value );
			if ( m.find()  ) {
				setFileLineContinuation( false );
				return lineNumber;
			} 
			
			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <fileLineContinuation> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)regexCommentRegex" ) ) {
			
			//
			// regexCommentRegexRegex
			//
			// we WiLL allow this to be null, thus meaning there are no commented lines in the REGEX block
			this.regexCommentRegex = value;
			return lineNumber;
			
		} else if ( name.matches( "(?i)regexEndBlockRegex" ) ) {
			//
			// regexEndBlockRegex
			//
			if ( (value == null) ) {
				if ( this.warnInvalidOption ) {
					System.err.println( "\nfatal: OPTION <" + name + "> cannot be null, on line <" + lineNumber + ">." );
				}
				System.exit( 11 );
			} else {
				this.regexEndBlockRegex = value;
				return lineNumber;
			}
			
		} else if ( name.matches( "(?i)dir(ectory)?PathString" ) ) {
			//
			// dirPathString
			//
			value = deQuote( value );
			
			if ( value == null || value.length() == 0 ) {
				// allow reset to null to override
				this.dirPathString = null;
				return lineNumber;		
			}
						
			File dirPathObj = new File( value );
								
			if ( ! dirPathObj.isDirectory() && this.warnInvalidOption ) {
				if ( Validate.testMode ) {
					System.out.println( "\nwarning: invalid VALUE <" + value + "> for  <dirPathString> on line <" + lineNumber + ">.");
					return lineNumber;
				} else {
					System.err.println( "\nfatal: invalid directory specified by dirPathString <" + value + "> on line <" + lineNumber + ">.");
					System.exit( 11 );	
				}
			} else {
				this.dirPathString = value;
				return lineNumber;	
			}
			
			dirPathObj = null; // no longer needed
			
		} else if ( name.matches( "(?i)errorReportDetails?" ) ) {
			//
			// errorReportDetails
			//
			value = deQuote( value );
			
			value = value.toLowerCase();
			
			if ( value.matches( "(?i)l\\w*" ) ) {
				this.errorReportDetails = 'l';
				return lineNumber;
			} else if ( value.matches( "(?i)n\\w*" ) ) {
				this.errorReportDetails = 'n';
				return lineNumber;
			} else if ( value.matches( "(?i)a\\w*" ) ) {
				this.errorReportDetails = 'a';
				return lineNumber;
			} else {
				System.err.println( "\nfatal: invalid VALUE <" + value + "> for <errorReportDetails> on line <" + lineNumber + ">.");
				System.exit( 44 );
			}
			
		} else if ( name.matches( "(?i)errorReportSummary" ) ) {
			//
			// errorReportSummary
			//
			value = deQuote( value );
			
			value = value.toLowerCase();
			
			if ( value.matches( "(?i)a\\w*|(?i)y\\w*" ) ) {
				this.errorReportSummary = 'a';
				return lineNumber;
			} else if ( value.matches( "(?i)f\\w*" ) ) {
				this.errorReportSummary = 'f';
				return lineNumber;
			} else {
				System.err.println( "\nfatal: invalid VALUE " + value + "> for <errorReportSummary> on line <" + lineNumber + ">.");
				System.exit( 44 );
			}
			
		} else if ( name.matches( "(?i)lineSkipRegex" ) ) {
			//
			// lineSkipRegex
			//
			if ( (value != null) ) {
				this.lineSkipRegex = value;
				return lineNumber;
			} else {
				if ( this.warnInvalidOption ) {
					System.err.println( "\nfatal: VALUE <lineSkipRegex> can't be null on line <" + lineNumber + ">.");
				}
				System.exit( 11 );
			}
			
		} else if ( name.matches( "(?i)lineCheckRegex" ) ) {
			//
			// lineCheckRegex
			//
			if ( (value != null) ) {
				lineCheckRegex = value;
				
				try {
					Pattern.compile( lineCheckRegex );
				} catch (final PatternSyntaxException e) {
					System.err.println("\nerror: option <lineCheckRegex>, on line <" + lineNumber + "> did not compile. "
									+ e.getMessage());
					System.exit(15);
				}
			}
				
			return lineNumber;
			
		} else if ( name.matches( "(?i)linePrefixRegex" ) ) {
			//
			// linePrefixRegex
			//
			if ( (value != null) ) {
				linePrefixRegex = value;
				
				try {
					Pattern.compile( linePrefixRegex );
				} catch (final PatternSyntaxException e) {
					System.err.println("\nerror: option <linePrefixRegex>, on line <" + lineNumber + "> did not compile. "
									+ e.getMessage());
					System.exit(15);
				}
			}
				
			return lineNumber;
			
		} else if ( name.matches( "(?i)lineReplaceRegex" ) ) {
			//
			// lineReplaceRegex
			//
			if ( value == null ) {
				System.err.println("\nerror: option <lineReplaceRegex>, on line <" + lineNumber + "> cannot be null. ");
				System.exit(15);
			}
			
			lineReplaceRegex = value;
				
			try {
				Pattern.compile( linePrefixRegex );
			} catch (final PatternSyntaxException e) {
				System.err.println("\nerror: option <linePrefixRegex>, on line <" + lineNumber + "> did not compile. "
								+ e.getMessage());
				System.exit(15);
			}
			
			return lineNumber;
			
		} else if ( name.matches( "(?i)lineReplaceDelimiterRegex" ) ) {
			//
			// lineReplaceDelimiterRegex
			//
			
			lineReplaceDelimiterRegex = value;

			try {
				Pattern.compile( linePrefixRegex );
			} catch (final PatternSyntaxException e) {
				System.err.println("\nerror: option <linePrefixRegex>, on line <" + lineNumber + "> did not compile. "
						+ e.getMessage());
				System.exit(15);
			}

			return lineNumber;
			
		} else if ( name.matches( "(?i)lineSuffixRegex" ) ) {
			//
			// lineSuffixRegex
			//
			if ( (value != null) ) {
				
				lineSuffixRegex = value;
				
				try {
					Pattern.compile( lineSuffixRegex );
				} catch (final PatternSyntaxException e) {
					System.err.println("\nerror: option <lineSuffixRegex>, on line <" + lineNumber + "> did not compile. "
									+ e.getMessage());
					System.exit(15);
				}
			}
				
			return lineNumber;
			
		} else if ( name.matches( "(?i)delimiterRegex" ) ) {
			//
			// delimiterRegex
			//
			
			try {
				Pattern.compile( value );
			} catch ( final PatternSyntaxException pse ) {
				System.err.println( "\nfatal: VALUE for delimiterRegex on line <" + lineNumber + "> did not compile.");
				System.exit( 11 );
			}

			delimiterRegex = value;
			
			return lineNumber;
			
		} else if ( name.matches( "(?i)showValidData" ) ) {
			//
			// showValidData
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setShowValidData( true );
				return lineNumber;
			} 

			m = falsePattern.matcher ( value );
			if ( m.find()  ) {
				setShowValidData( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <showValidData> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}	
			
		} else if ( name.matches( "(?i)showToolTitle" ) ) {
			//
			// showToolTitle
			//
			value = deQuote( value );
			
			m = truePattern.matcher ( value );
			if ( m.find() ) {
				setShowToolTitle( true );
				return lineNumber;
			} 

			m = falsePattern.matcher ( value );
			if ( m.find()  ) {
				setShowToolTitle( false );
				return lineNumber;
			} 

			// else 
			m = null;
			if ( this.warnInvalidOption ) {
				System.out.println( "\nwarning: OPTION <showToolTitle> has an invalid VALUE <" + value + "> on line <" + lineNumber + ">." );
				return lineNumber;
			}	
					
		} else if ( this.warnInvalidOption ) {
			// final catch all
			System.out.println( "\nwarning: OPTION <" + name + "> was not recognized, line <" + lineNumber + ">." );
			return lineNumber;
		} 

		return lineNumber;

	} // end of optNameValue()
	
	
	// strip off quotes is they start and end the string
	static String deQuote( String value ) {
		
		if ( value.length() > 2 && value.startsWith("\"") && value.endsWith("\"") ) {
			value = value.substring(1, value.length() - 1);
		}
		
		return value;
	}

	

	
}
