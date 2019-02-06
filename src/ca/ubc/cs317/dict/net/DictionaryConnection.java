package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.exception.DictConnectionException;
import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;
import ca.ubc.cs317.dict.util.DictStringParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

import static java.lang.System.out;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private Map<String, Database> databaseMap = new LinkedHashMap<String, Database>();

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        
        String fromServer ;

        try {
            // create socket and initialize streams
            socket = new Socket(host , port) ;
            output = new PrintWriter(socket.getOutputStream(), true) ;
            input  = new BufferedReader(new InputStreamReader(socket.getInputStream())) ;

            if (((fromServer = input.readLine()) != null) &&
                    (fromServer.substring(0, 3).equals("220"))){
                //out.println("Server: " + fromServer);
            }
            else {
                throw new DictConnectionException("Wrong Status Code");
            }
        } catch (UnknownHostException e) {              //exception handling if server doesn't exist or respond
            throw new DictConnectionException(e);
        } catch (IOException e) {
            throw new DictConnectionException(e) ;
        }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {

        String userInput  ;
        String fromServer ;
        try {
            userInput = "QUIT";
            out.println("Client: " + userInput);
            output.println(userInput);                       // send QUIT command
            fromServer = input.readLine();
            //out.println("Server: " + fromServer);

            if (fromServer.substring(0, 3).equals("221")){   // 221: connection is closed successfully
                socket.close();
                System.exit(0);
            }
        } catch (IOException e) {
            // ignore any exception
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        getDatabaseList(); // Ensure the list of databases has been populated

        String userInput;
        String fromServer;

        // DONE Add your code here

        try {
            userInput = "D " + database.getName() + " "  + "\""+word+"\"";
            //out.println("Client: " + userInput);
            output.println(userInput);              // send DEFINE command
            Definition def  = null;
            String check  = input.readLine();      // check = initial status line
            //out.println("Server: " + check);

            if (check.contains("552")) {           // 552: no match
                return set ;
            }
            if ( check.contains("150")) {          // 150: successfully found definitions

                while ( (fromServer = input.readLine()) != null) {

                    if (fromServer.contains("250 ok")) {
                        out.println("Server: Successfully found definition ");
                        break;
                    }
                    if (fromServer.contains("151")) {
                        String[] temp = DictStringParser.splitAtoms(fromServer);
                        Database db = new Database(temp[2], temp[3]);
                        def = new Definition(temp[1], db);          // creates def with proper word and database
                        set.add(def);

                    }else if (fromServer.equals(".")){
                        // appends definition with "." removed
                    }else {
                        assert def != null;
                        def.appendDefinition(fromServer);
                    }
                }
            }else {
                throw new DictConnectionException() ;   // all other status codes throw exceptions
            }
        }catch (IOException e){
            throw new DictConnectionException() ;
        }
        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();
        
        // DONE Add your code here
        String userInput;
        String fromServer;

        try {
            userInput = "MATCH " + database.getName() + " " + strategy.getName() + " " + "\""+word+"\"" ;
            //out.println("Client: " + userInput);
            output.println(userInput);

            String check  = input.readLine();
            //out.println("Server: " + check);

            if (check.contains("552")) {    // 552: no match
                return set ;
            }
            if (check.contains("152")) {    // 152: successfully found matches

                while ((fromServer = input.readLine()) != null) {
                    if (fromServer.contains("250 ok")) {
                        //out.println("Server: Successfully found match ");
                        break;
                    }
                    if (fromServer.contains("552 no match")) {
                        //out.println("Server: " + fromServer);
                        break;
                    }
                    if (!(fromServer.equals("."))) {
                        String[] temp = DictStringParser.splitAtoms(fromServer);
                        set.add(temp[1]);
                    }
                }

            }else {
                throw new DictConnectionException() ;   // all other status codes throw exceptions
            }
        }catch (IOException e){
            throw new DictConnectionException() ;
        }
        //out.println("Server: " + set);
        return set;
    }

    /** Requests and retrieves a list of all valid databases used in the server. In addition to returning the list, this
     * method also updates the local databaseMap field, which contains a mapping from database name to Database object,
     * to be used by other methods (e.g., getDefinitionMap) to return a Database object based on the name.
     *
     * @return A collection of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Database> getDatabaseList() throws DictConnectionException {

        if (!databaseMap.isEmpty()) return databaseMap.values();

        // DONE Add your code here
        String userInput;
        String fromServer;

        try {
            userInput = "Show DB";
           // out.println("Client: " + userInput);
            output.println(userInput);

            String check  = input.readLine();
            //out.println("Server: " + check);

            if (check.contains("554")) {        // 554: no database present
                return databaseMap.values() ;
            }
            if ( check.contains("110")) {       // 110: n databases present

                while ((fromServer = input.readLine()) != null) {

                    if (fromServer.contains("250 ok")) {
                       // out.println("Server: Successfully shown DB");
                        break;
                    }
                    if (fromServer.contains(" ")) {     // lines that contain spaces (" ") represent databases
                        String[] temp = DictStringParser.splitAtoms(fromServer);
                        Database db = new Database(temp[0], temp[1]);
                        databaseMap.put(db.getName(), db);
                    }
                }
            }else {
            throw new DictConnectionException() ;   // all other status codes throw exceptions
        }
        }catch (IOException e){
            throw new DictConnectionException(e) ;
        }
        return databaseMap.values();
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();
        // DONE Add your code here

        String userInput;
        String fromServer;

        try {
            userInput = "Show STRAT";
            //out.println("Client: " + userInput);
            output.println(userInput);

            String check  = input.readLine();
            //out.println("Server: " + check);

            if (check.contains("555")) {        // 555: no strategies available
                return set ;
            }
            if ( check.contains("111")) {       // 111: n strategies available

                while ((fromServer = input.readLine()) != null) {

                    if (fromServer.contains("250 ok")) {
                        //out.println("Server: Successfully shown STRAT");
                        break;
                    }
                    if (fromServer.contains(" ")) {  // lines that contain spaces (" ") represent strategies
                        String[] temp = DictStringParser.splitAtoms(fromServer);
                        MatchingStrategy ms = new MatchingStrategy(temp[0], temp[1]);
                        set.add(ms);
                    }
                }
            }else {
                throw new DictConnectionException();  // all other status codes throw exceptions
            }
        }catch (IOException e){
            throw new DictConnectionException(e) ;
        }

        return set;
    }

}
