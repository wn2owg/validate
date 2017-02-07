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
 * Regex - open and reads a file with a Regex to test against test cases
 * this class is almost like a stand alone tool for developers and/or testers to
 * make sure thier regex is accurate before incorporating into another tool or code
 */
package validate;

/**
 * @author Bill Lanahan
 *
 */

import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.*;
import java.util.Arrays;

class Regex {

	private Pattern pattern;
	private Pattern commentPattern = null;
	private Matcher matcher = null;
	private Matcher commentMatcher;
	private boolean found;
	private short lineNumber = 0;
	private int start, end;
	private String REGEX;
	private String INPUT;
	private String regexCommentString = null;
	private File file = null;
	private boolean regexMultiLine = false;
	private boolean printTCcomment = false;
	private String fileNameRegex;

	Regex() {
		// nothing
	}

	Regex( String fileNameRegex, String regexCommentString, boolean regexMultiLine, boolean printTCcomment ) {
		this.regexCommentString = regexCommentString;
		this.regexMultiLine = regexMultiLine;
		this.printTCcomment = printTCcomment;
		this.fileNameRegex = fileNameRegex;
		file = new File( fileNameRegex );
		
		System.out.println("\n                               Validate (REGEX Tester)\n" +
				"                               =======================");
	}

	void readFile() {
	
		final String title = "\n== Test REGEX ==\n";

		BufferedReader buffReader = null;

		try {
			buffReader = new BufferedReader( new FileReader(this.file) );
			
			if ( regexCommentString != null ) {
				commentPattern = Pattern.compile( regexCommentString );
			}
			
			String tmpline = new String();
			boolean continuationState = false;
			
			while( (REGEX = buffReader.readLine()) != null )  {
				lineNumber++;

				if ( regexCommentString != null ) {
					commentMatcher = commentPattern.matcher( REGEX );
					
					if ( commentMatcher.find() ) {
						// swallow comments
						continue;
					}
				}

				
				// allows regex to be multi-line with final backslash if option -M used					
				if ( this.regexMultiLine ) {

					if ( REGEX.endsWith("\\") && ! REGEX.endsWith("\\\\")  ) {
						
						// its a continuation; need to adjust line and concatenate
						if ( continuationState == false ) {
							// swallow the \
							continuationState = true;
							tmpline = REGEX.substring(0, REGEX.length() - 1 ); // trims the backslash
							lineNumber++;
							continue;

						} else {
							// not the final line, so append and read more
							REGEX = REGEX.trim(); // swallows the start of line WS
							REGEX = REGEX.substring(0, REGEX.length() - 1); // trims the backslash
							tmpline += REGEX;//
							lineNumber++;
							continue;

						}

					} else {
						
						if ( continuationState ) {
							
							// the final line now, so trim and use it
							REGEX = REGEX.trim();
							lineNumber++; 
							tmpline += REGEX;
							continuationState = false; // reset
							REGEX = tmpline;
						} else {
							// line used as is
							REGEX = tmpline;
							lineNumber++;
						}
					}
				}
				
				if ( this.regexMultiLine && REGEX.length() < 1) {
					System.err.println("fatal: -M used but no line continuation found in REGEX\n");
					System.exit(100);
				}
				

				// this is the first non-comment line so its the REGEX
				pattern = Pattern.compile( REGEX );

				System.out.println( title + REGEX + "\n" );
				REGEX = null;
				break;
			}

		} catch ( final PatternSyntaxException pe ) {
			System.out.println("REGEX: " + REGEX + " Did not compile correctly, check REGEX syntax.");
			System.exit( 12 );
		} catch ( final FileNotFoundException e ) {
			System.err.println( "\nfatal: could not locate file <" + fileNameRegex + ">." );
			System.exit( 12 );
		} catch (final IOException ioe) {
			System.out.println("\nfatal: I/O error " + ioe.getMessage() );
			System.exit( 13 );
		}

		try {
			System.out.println( "== Match test(s) ==" );

			while( (INPUT = buffReader.readLine()) != null )  {
				lineNumber++;

				if ( regexCommentString != null ) {
						commentMatcher = commentPattern.matcher( INPUT );
						
						if( commentMatcher.find() ) {
							// swallow comments
							
							if ( this.printTCcomment ) {
								// print the test case comment for the user
								System.out.println( INPUT );
							}
							continue;
						}
				}

				final char[] marker = new char[ INPUT.length() ];
				matcher = pattern.matcher( INPUT );
				Arrays.fill(marker, ' ');

				found = false;
				while ( matcher.find() ) {
					start = matcher.start(); 
					end = matcher.end();
					
					System.out.println( "Matched: line <" + lineNumber + "> start column <" + (start+1) + "> end column <" + (end) + ">." );
					Arrays.fill( marker, start, end, '=' );
					found = true;
				}

				if ( found ) {
					System.out.println(INPUT);
					System.out.println(new String(marker) + "\n");
				} else {
					System.out.println("No match: line <" + lineNumber + ">.\n" + INPUT + "\n");
					found = false;
				}

			}
		}	 catch (final IOException e) {
			System.err.println( "\nfatal: problem reading <" + fileNameRegex + ">. + ge.getMessage()" );
			System.exit( 13 );
		} finally {
			try {
				buffReader.close();
			} catch (final IOException e) {
				System.err.println( "\nfatal: There was an error, cleaning up resources." );
				System.exit( 13 );
			}
		}

		System.out.println( "" );
	}


}
