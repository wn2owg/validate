package validate;

import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.*;

/* RegexTimed specifically created to check performance of a test REGEX
 * Minimal output is used - this is stripped down from Regex which is expected
 * to be used first to be sure the REGEX you are testing is good
 */
class RegexTimed {
	
		private Pattern pattern;
		private Matcher matcher;
		private short found = 0;
		private short lineNumber = 0;
		private String REGEX;
		private String INPUT;
		private String fileNameRegex;
		private short MAX_LINES = 5000; // limited so we can read out of memory instead of the file
		private File file = null;
		private Pattern commentPattern = null;
		private Matcher commentMatcher;
		private String regexCommentString = null;
		private boolean regexMultiLine = false;
		private boolean continuationState = false;

		RegexTimed() {
			// nothing
		}

		RegexTimed( String fileNameRegex, String regexCommentString, boolean regexMultiLine ) {
			this.fileNameRegex = fileNameRegex;
			file = new File( fileNameRegex );
			this.regexCommentString = regexCommentString;
			this.regexMultiLine = regexMultiLine;
			
			System.out.println("\n                               Validate (REGEX Tester)\n" +
								"                               =======================");
		}

		void readFile() {
			final String[] testStr = new String[MAX_LINES];
			final String title = "\n== Timed Test for REGEX ==\n";

			BufferedReader buffReader = null;
			String tmpline = new String();

			try {
				buffReader = new BufferedReader( new FileReader(this.file) );

				if ( regexCommentString != null ) {
					commentPattern = Pattern.compile( regexCommentString );
				}

				while( (REGEX = buffReader.readLine()) != null )  {
					lineNumber++;
					
					// strip comments
					if ( regexCommentString !=null ) {
						commentMatcher = commentPattern.matcher( REGEX );
					
						if ( commentMatcher.find() ) {
							// swallow comments
							continue;
						}
					}
					
					// allows regex to be multiline with final backslash if option -M used					
					if ( regexMultiLine ) {

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
					
					if ( regexMultiLine && REGEX.length() < 1) {
						System.err.println("fatal: -M used but no line continuation found in REGEX\n");
						System.exit(100);
					}
					
					pattern = Pattern.compile( REGEX );

					System.out.println( title + REGEX + "\n" );
					REGEX = null;
					break;
				}

			} catch ( final PatternSyntaxException pe ) {
				System.out.println("\nfatal: " + REGEX + " Did not compile correctly, check REGEX syntax.");
				System.exit( 12 );
			} catch ( final FileNotFoundException e ) {
				System.err.println( "\nfatal: could not locate file <" + fileNameRegex + ">." );
				System.exit( 12 );
			} catch (final IOException ioe) {
				System.out.println("\nfatal: I/O error " + ioe.getMessage() );
				System.exit( 13 );
			}

						
			try {
				System.out.println( "\n== Reading test string(s) into memory ==" );
								
				lineNumber = 0; //reset since we used one for REGEX
				
				while( ((INPUT = buffReader.readLine()) != null) && (lineNumber < MAX_LINES) )  {

					if ( regexCommentString != null ) {
						commentMatcher = commentPattern.matcher( INPUT );
						
						if ( commentMatcher.find() ) {
							// swallow comments
							continue;
						}
					}

					// else its a good test case to add to memory
					testStr[lineNumber++] = INPUT;
				}

			} catch (final IOException e) {
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

			
			// ok data in memory ready to do timed tests
			
			final long start = System.nanoTime();
			long end = 0;
			
			for ( short i = 0; i < lineNumber; i++ ) {
				matcher = pattern.matcher( testStr[i] );
					
				if ( matcher.find() ) {
					found++;
				}
										
			}

			end = System.nanoTime();
			final long diff = end - start;
			
			System.out.println( found + " of " + lineNumber + "  matches were made in <" + diff + "> nano seconds" );
			
			if ( (found > 0) && (diff  > 0) ) {
				System.out.print( "\n" + ((end - start)/found) +  " nano second(s) per match\n");
			}
		}


	}

