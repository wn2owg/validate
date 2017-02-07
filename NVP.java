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

import java.util.regex.PatternSyntaxException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * NVP - NameValuePair - holds the  data from the user about validating a single Name
 * @author Bill Lanahan
 *
 */
class NVP {
	private String name = null;
	private String value = null;
	private short lineNumber = 0;
	private Pattern valuePattern = null;
	private Matcher m = null;
	
	
	NVP (String name, String value, short lineNumber) {
		this.name = name;
		this.value = value;
		this.lineNumber = lineNumber;
	}
	
	/* we do this outside the constructor so that we can get a return code enabling us 
	 * to continue even if it fails
	 */
	boolean compilePattern() {
		// take the value (which is the regex from the conf file) and compile and save it
		try {
			valuePattern = Pattern.compile( value ); 
			return true;
		} catch ( final PatternSyntaxException pse ) {
			System.err.println( "\nerror: NAME <" + name 
					+ "> invalid regular expression VALUE <" 
					+ value + "> on line <" + lineNumber );
			return false;
		}
	}
	
	short getLineNumber() {
		return this.lineNumber;
	}
	
	String getValue() {
		return this.value;
	}
	
		
	// test the regex stored in this object against the valve from name=value read
	// in from the property file or config file
	boolean validateValueWithRegex( String valueToTest ) {
			
		m = valuePattern.matcher( valueToTest );
		
		if ( m.find() ) {
			return true;
		} else { 
			return false;
		}
	}
	
}
