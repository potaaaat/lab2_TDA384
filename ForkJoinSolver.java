package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.Spliterator;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.*;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver
    extends SequentialSolver
{
    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze   the maze to be searched
     */
    public ForkJoinSolver(Maze maze)
    {
        super(maze);
        initStructures();
    }
    
    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze        the maze to be searched
     * @param forkAfter   the number of steps (visited nodes) after
     *                    which a parallel task is forked; if
     *                    <code>forkAfter &lt;= 0</code> the solver never
     *                    forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter)
    {
        this(maze);
        this.forkAfter = forkAfter;
        initStructures();
    }
    
    public ForkJoinSolver(Maze maze, int forkAfter, int start, ConcurrentSkipListSet<Integer> visited, ConcurrentHashMap<Integer, Integer> predecessor, AtomicBoolean heartFound)
    {
        this(maze);
        this.forkAfter = forkAfter;
        this.visited = visited;
        this.heartFound = heartFound;
        this.predecessor = predecessor;//new predesseor and frontier because different players are walking different paths
        this.start = start; //new start position for the thread
        frontier = new ConcurrentLinkedDeque<Integer>(); //new frontier list for thread
        
    }

    
    protected void initStructures() {
    	visited = new ConcurrentSkipListSet<Integer>();
    	//atomic boolean to keep track of when we want to terminate all threads when the goal has been found.
    	heartFound = new AtomicBoolean(false); //if a thread has found the goal this value will chnge to true
    	//if true the other threads need to be terminated: TODO the termination function. 
    	predecessor = new ConcurrentHashMap<>();
    	frontier = new ConcurrentLinkedDeque<Integer>();
    	beginning = this.start; //the initial start position
    	
    }
    protected int player;
    protected ConcurrentSkipListSet<Integer> visited;
    protected int forkAfter;
    protected AtomicBoolean heartFound;
    protected ConcurrentHashMap<Integer, Integer> predecessor;
    protected ConcurrentLinkedDeque<Integer> frontier;
    protected int beginning;
    /**

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return   the list of node identifiers from the start node to a
     *           goal node in the maze; <code>null</code> if such a path cannot
     *           be found.
     * 
     * 
     * Concurrent skip list set :)
     */
    @Override
    public List<Integer> compute()
    {
    	forkAfter = 3;
    	return parallelSearch();
    }
    

    private List<Integer> parallelSearch()
    {
    	
    	//System.out.println("created " + player); 
    	//define the forks but not do anything with them yet
    	//if never forked they need to be defined for the joins
        ForkJoinSolver fork1 = null;
        ForkJoinSolver fork2 = null;      

        //double checking for races before spawning a player
    	if(!visited.contains(start)){
    		player = maze.newPlayer(start); //player is created
    		frontier.push(start);} //start space is added to frontier
    	//System.out.println("babys first block " + frontier);
    	
    	//keep start where we have 1 player and put the start box in frontier
    	while(!frontier.isEmpty()) {
    		
    		if(heartFound.get() == true) {break;}
    		if(frontier.size() > forkAfter) {//if bigger than forkAfter we want to split into more threads
//    			System.out.println("frontierlist: " + frontier);
    			int popper = frontier.pop();
    			//frontier node is taken out and checked if it is a node included in visited
            	if(!visited.contains(popper)) {
            		//if node has not been visited create fork1
            		fork1 = new ForkJoinSolver(this.maze, this.forkAfter, popper, this.visited, this.predecessor, this.heartFound);            	
            		fork1.fork();}
            	//repeat taking a frontier node to chech if it is a node included in visited
            	popper = frontier.pop();
            	if(!visited.contains(popper)) {
            		//if so create fork2 
            		fork2 = new ForkJoinSolver(this.maze, this.forkAfter, popper, this.visited, this.predecessor, this.heartFound);
            		fork2.fork(); //start fork
            		}


    		}
    		else {
//    			System.out.println("Trying to move");
	            // get the new node to process
	            int current = frontier.pop();
	            // if current node has a goal
	            if (maze.hasGoal(current)) { 
	            	heartFound.set(true); //the heart is found, set it as true to start the process of terminating all other threads
	            	// move player to goal
	                maze.move(player, current);
	                // search finished: reconstruct and return path
	                return pathFromTo(beginning, current);
	            }
	            //check if current node is included in visited list, if not it adds the node to visited it goes into the next node procedure
	            if (visited.add(current)) { 
	            	// move player to current node
	            	maze.move(player, current);
	                // for every node nb adjacent to current
	                for (int nb: maze.neighbors(current)) {
	                    // add nb to the nodes to be processed
	                    if(!visited.contains(nb)) {frontier.push(nb);}
	       
	                    // if nb has not been already visited,
	                    // nb can be reached from current (i.e., current is nb's predecessor)
	                    if (!visited.contains(nb))
	                        predecessor.put(nb, current); //add to predecessor map
	                }
	               
	            }
    	        	    
    		}
    		
    	}
    	System.out.println("before join " + player); 
    	List<Integer> result1 = null;
    	if(fork1 != null) { //has fork1 been created
    		result1 = fork1.join();}//wait for join on fork1
    	
//    	System.out.println("between join " + player);
    	
    	List<Integer> result2 = null; 
    	if(fork2 != null) //has fork2 been ceated 
    		{result2 = fork2.join();} //wait for join on fork2
//    	System.out.println("after join " + player);
    	
    	if (result1 != null) { //check if result has heartFound
    		return result1;
    	}
    	if (result2 != null) {//check if result has heartFound
    		return result2;
    	}
    	
        return null;	//if no result != heartfound, return null
    }
    
    protected List<Integer> pathFromTo(int from, int to) {//had to put this here, otherwise the program complained that predecessor was Null
        List<Integer> path = new LinkedList<>();
        Integer current = to;
        while (current != from) {
            path.add(current);
            current = this.predecessor.get(current);
            if (current == null)
                return null;
        }
        path.add(from);
        Collections.reverse(path);
        return path;
    }
}
