A FEW DISCLAIMERS AND JUSTIFICATIONS
====================================

I've always like regular expressions (regexes) and used them extensively with grep, awk, lex, perl, and C. I was surprised when I saw that many
very skilled developers didn't seem as well versed in their use as I had expected. A second observation in recent years was that though 
some of code was widely used, and very stable, there would be random production bugs - after investigation a fair number of times it was found that the code was fine,
but that some piece of "configuration data" was the problem. That is, a name value pair like: TIMEZONE=GNT instead of GMT, or row and column data file that 
was used by an install utility to preload a database table, had a problem (bad data in a field, to many or too few fields, etc.)

So this tool is an attempt to provide a reasonable solution to both of the above.

I tried to provide a lot of flexibility in the formatting of the files that the tool uses. They were to accomodate many of the complaints come across in
tools in the past; and in some cases to simplify or at least not cause conflicts with the parsing of regular expressions. In short, in many cases your initial
thought might be "why didn't he just use XXX as in Java or C?" or "who cares if a block is called "OPTIONS, or options, or OpTionS", they were all intentional.

Also I tried to pick defaults for almost everything, but leave it to the user to override them or even explicitly restate them as means of self ddocumentation.

I cannot actively support the code, but would like to hear from users if and how it benefits you, certainly enhance it for other file formats or condtions.

( Email: wn2owg@gmail.com with sublect VALIDATE )


CONTENT
=======

This file contains some high level descriptions that may be useful to those that may want
to enhance the validate tool.

To try or use the tool and understand its capabilities and hopefully benefits read the manpage. I deviated from the more cryptic manpage style and
added examples to the manpage rather than having a separate tutorial document, which would likely get separated from the source code.


All testing has been with java 1.6 and 1.7, in both linux and Windows - absolutely needs at least java 1.5.

There are many comments in the code, as well as some preamble text in source files.

For users executing the tool, there are a few options that write useful data to stdout. For example a dump of Java supported regexes so you don't have to use a book
or javadoc, a full list of options that may be specified in the OPTIONS block, etc.


FILES
=====

validate.1 -  manpage for use in linux(unix). Since this provides a lot of detail about the tools use, options, and most importantly the format of a 
configuration file, it is also included as validate.1.txt in case you are not in a linux environment.

 
MACROS.txt - a "starting" point for some macros that a user might want. This is an optional runtime file.
Its purpose is to give the tool user a simple and less error prone way to  reuse previously developed and tested regular expressions, in the same way
that a variable is used to insert a value into code. The macros get used in the REGEX block of a configuration file.


 
Validate.class -
        1- provides all the help messages
        2- parses and confirms combinations of options
        3- if more than one user data file(s) is used it puts the names in a data structure which can be iterated over.
        4- if -R is used properly (with -C and or -M) this means the user ONLY wants the behavior where regexes can be developed and tested (see Regex.class).
		
        5- if -T is used properly (with -C and or -M) this means the user wants a TIMING test of a regex developed in #4.
		   No I/O is included or compile time for regex; also the test cases are read from memory instead of a file expecting that to be more like
		   actual usage would be.
 
Regex.class - once called (by -R or -T) this provides features that enable a developer to test a java compatible regex against one or more test cases. This gives the 
         developer a simple sandbox with built in diagnostics so he/she can then confidently insert thie regex into the environment which it is intened for
		 with mininimal addational testing required. The class then and finishes the program. 

        -R  
        This reads a file with a regex and test cases to match against, one per line. Comments are supported if -C is given. The regex
        for a comment line (typically ^\s*#|^\s*$). 
		
		For each test case it tells if the regex matched and where on the line via column numbers and under lining.	 Also line references to the -R file 
		are used to help the user.
 
		-T
		Similar to (-R), however the intent now is to provide runtime timing, so a developer might want to try different alternatives of to match with 
		quntifiers|qualifiers|lookarounds fo the same pattern in case  they have vey time sensitive code. For example, to match a string of 5 digits, any of 
		the following regexes may be used: \d\d\d\d\d, \d{5}, \d{5,5}, [0-9]{5}.
        
		The match execution time is provided in NANO seconds.
		
		Use of the -T testing should only be done after succesful testing with -R becasue to make the _T light weight in terms of time, there is no diagnostic I/O,
		the test case are read into and then out of memory rather than the file to eliminate file access etc.
        
 
ParseConfig.clsss - If neither -R or -T was used, then the user want the other (much more significant behavior of the tool).

		This behavior is meant to validate confiuration data (in several formats within a flat ascii file) so that production code will not have to be burdened by
		runtime perfomance of data checking, logging, and error handling since the executable should never "see" invalid data. (Of course it assumes regexes have
		been rigourous tested, the validate tool has been used appropriately, and humans havehad the good sense not to corrupt the data after it has been validated.)
		
        This desired behavior is signalled by the use of -c configFile.
        
		The class can analyse and validate input data in several formats, and the user has control of many options as to what to consider as warnings, or errors;
		as well how information is displayed for diagnostic purposes (ranging from a cryptic return code status, to verbose details).
        in various format files.
 
        Some of what this class does is:
 
        1- opens and reads the config file
        2- keeps track of lineNumber, fileName, blockType for diagnostics
        3- for blocks (code begun with default %% BlockType and end with %%) makes sure blocks
           are in the right order (if they exist) OPTIONS, MACROS, RULE. 
		   
		   Make sure OPTIONS and MACROS can have 0|1 instance; 
		   RULE must have at least one instance.
           REGEX is a sub block of RUL.
		   
		   All blocks properly end.
		   
        4- Discards (but counts) any line that is NOT within a block (gives a lot of comment flexibility to the config file).
        5- Creates a default OPTION.class.
        6- once its in specific blocks (REGEX, MACRO, etc.) passes read lines for the dta file to be verified to other classes so
           they can be added to necessary data structures.
 
Macros.class - reads and stores the regexes if a macros file was supplied. If an
         optional MACROS.block is in the config file, then one line at a time is added to the data structure.
 
Options.class - a default class is created with getters and setters for option values; This class is then cloned
        for every RULE block so that each RULE has its own copy that can be enhanced by local settings. That is each RULE
		can have different options (local variables) than a previous or following RULE.
 
Rule.class - this class has a section for each format supported (JAVA|NVP|CUSTOM|LINES|FIELDS) dependent on each
        one it reads and manages the testing of the data line with the regexes appropraite for this format.
		
        Eventually this uses the various options to determine what to show the user if a warning or error
        was encountered. This class is kind of monoithic and has duplicate code thhat could have been organized
        better if at the start there ahd been a better plan. If in the future other formats were to be added
        this should probably be reorganized to be more OO.
 
Regex.class - processes the regexes in the REGEX block. Makes sure every regex compilesin part and as a whole depending
        on format. Stores them in data structures.
 
NVP.class - deal with breaking up name=value and checking if parts were already
defined, formatted correctly, etc.
 
 
BUILDING and INSTALLING
=======================

Nothing fancy needed. Just used javac, and put the class files in your CLASSPATH. In eclipse, just list all the source, and have the
 bin thats created with the classes put in the CLASSPATH.
 
 GETTING STARTED
 ================
 
 Here's a typical scenario that a developer may encounter, so its also a good introduction to the behaviors of the tool.
 
 Since you read this far, at least skim through the manpage, then consider this:
 
 Let's say you need to create or concatenate some data for your application into a field separated file (.i.e. /etc/password or a DB table),
 You know or are given the specifications of each data element (field) so you want to use "validate" to prefind data issues, here's how you 
 might start.
 
 1- examine the first field and create a regular expression that defines it (or said another way one that can be used to validate it).
    Need help with regular expressions? 
	
	Run: validate ?
	
	See that option -p REGEX will show you all the Java supported regular expressions. Let's your first field is a trivial 2 digit number
	for the minimum age to drive in a particular state (e.g. 14-18). So you decide 1[4-8] should work (maybe).
	
2- Try the regex test behavior.

	Create a flat ascii test file (MyTest) with the regex to test on line 1, and each test case on another line (we'll add a cooment line followed by some
	negative test cases), 
	
	1[4-8]
	14
	15
	18
	## the following should fail
	0
	13
	19
	Hello World
	
	Run: validate -R MyTest
	
	Review the results. Tune as needed, and repeat. (Beware: I'm leading you over a cliff. What happens with "I am 15 years old"? You may want to
	supplement your regex with "\b". See: validate -p REGEX.
	
3- Repeat the above until you have a working and tested regex for each field. Now you are ready for the datafile validation behavior.

   From the manpage, you know you want to deal with FIELD formatted data. You need a config file which will tell validate about you fields and options
   about output etc.
   
   Run: validate -p configFile > MyConfigFile
   Using the comments in the file and the manpage edit the file (you can probably let almost everything use defaults, with the exception of specifying
   the field deliminter).
   
   Enter the regexes you developed into the REGEX block, of a RULE block. Specify the file name and format in the rule block.
   Run: validate -c MyConfigFile -f MyDataTestFile  (where MyDataTestFile is the field separated file with actual data .e.g. several lines of /etc/passwd).
   
   See what stdout/error have to say about your validation.
   
   In the event that the example you tried was so trivial that everything was perfect, I'd recommend purposely make a field or two INVALID so you can see
   the default behaior of the tool and how it can easily help you find items to correct.
   
   
   
   There are always going to be software bugs. Use automation in as many phases of the development, manufacture, install, turn-up, periodic (by cron or 
   administratively) as possible, so you see them before your customer is impacted by them.
  
