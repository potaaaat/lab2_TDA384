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
    
    public ForkJoinSolver(Maze maze, int forkAfter, ConcurrentLinkedDeque<Integer> frontier, ConcurrentSkipListSet<Integer> visited, ConcurrentHashMap<Integer, Integer> predecessor, AtomicBoolean heartFound)
    {
        this(maze);
        this.forkAfter = forkAfter;
        this.visited = visited;
        this.heartFound = heartFound;
        this.predecessor = predecessor;//new predesseor and frontier because different players are walking different paths
        this.start = frontier.getFirst(); //new start position for the thread
        this.frontier = frontier; //take old threads frontier list
        
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
    	forkAfter = 1;
    	//System.out.println(forkAfter);
    	return parallelSearch();
    	
    }
    
    private ForkJoinSolver fork1 = null;
    private ForkJoinSolver fork2 = null;
    private ForkJoinSolver fork3 = null;
    private ForkJoinSolver fork4 = null; 
    private int hasForked;

    private List<Integer> parallelSearch()
    {
    	
    	//System.out.println("created " + player); 
    	//define the forks but not do anything with them yet
    	//if never forked they need to be defined for the joins
    	hasForked = 0; //just spawned, no childeren have been created
        //double checking for races before spawning a player
    	if(!visited.contains(start)){
    		player = maze.newPlayer(start); //player is created
    		frontier.push(start);} //start space is added to frontier
    	
    	//keep start where we have 1 player and put the start box in frontier
    	while(!frontier.isEmpty()) {   
//    		System.out.println(frontier);
    		if(heartFound.get() == true) {break;}
    		if(hasForked == 4) {break;} //parent waits if it is done spawning childeren
    		if(frontier.size() > forkAfter && hasForked < 3) {//if bigger than forkAfter we want to split into more threads
//    			System.out.println("frontierlist: " + frontier);
    			System.out.println(frontier);
    			int popper = frontier.pop();
    			//frontier node is taken out and checked if it is a node included in visited
            	if(!visited.contains(popper) && hasForked == 0) { //has thread created first child
            		//if node has not been visited create fork1
            		fork1 = new ForkJoinSolver(this.maze, this.forkAfter, popper, this.visited, this.predecessor, this.heartFound);            	
            		fork1.fork();
            		hasForked += 1; //thread has forked once
            		}
            	//repeat taking a frontier node to chech if it is a node included in visited
            	if(!frontier.isEmpty() && hasForked == 1) {
	            	//make sure that the frontier is not empty
	            	popper = frontier.pop();
	            	if(!visited.contains(popper)) {//has thread created second child
	            		//if node has not been visited create fork2 
	            		fork2 = new ForkJoinSolver(this.maze, this.forkAfter, popper, this.visited, this.predecessor, this.heartFound);
	            		fork2.fork(); //start fork
	            		hasForked += 1; //thread has forked twice
	            		}
            	}
            	//repeat taking a frontier node to chech if it is a node included in visited
            	if(!frontier.isEmpty() &&hasForked == 2) {//make sure that the frontier is not empty
	            	popper = frontier.pop();
	            	if(!visited.contains(popper)) { //has thread created third child
	            		//if so create fork3 
	            		fork3 = new ForkJoinSolver(this.maze, this.forkAfter, popper, this.visited, this.predecessor, this.heartFound);
	            		fork3.fork(); //start fork
	            		hasForked += 1; //thread has forked thrice
	            		}
            	}
            	//repeat taking a frontier node to chech if it is a node included in visited
            	if(!frontier.isEmpty() &&hasForked == 3) {//make sure that the frontier is not empty
	            	popper = frontier.getFirst();
	            	if(!visited.contains(popper)) { ////has thread created fourth child
	            		//if so create fork4, fork 4 takes over parents role, as to avoid the parent starving it's childeren 
	            		fork4 = new ForkJoinSolver(this.maze, this.forkAfter, frontier, this.visited, this.predecessor, this.heartFound);
	            		fork4.fork(); //start fork
	            		hasForked += 1;
	            		//noMoreForks = true;//thread has forked four times, should not fork more times
	            		}
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
    	//No more nodes to explore, now we need to check and see if we need to wait for forks to join before we return null (a child could have found the heart!)
    	//check for fork1
    	List<Integer> result1 = null;
    	if(fork1 != null) { //check if fork1 been created
    		result1 = fork1.join();}//wait for join on fork1
    	
    	if (result1 != null) { return result1;}//check if result has heartFound
    	
    	//check for fork2
    	List<Integer> result2 = null; 
    	if(fork2 != null) //check if fork2 been ceated 
    		{result2 = fork2.join();} //wait for join on fork2
    	
    	if (result2 != null) {return result2;}//check if fork2's result has heartFound
    	
    	//check for fork3
    	List<Integer> result3 = null; 
    	if(fork3 != null){//check if fork3 been ceated 
    		result3 = fork3.join();} //wait for join on fork3
    	
    	if (result3 != null){ return result3;}//check if result3 has heartFound
    	
    	//check for fork4
    	List<Integer> result4 = null; 
    	if(fork4 != null) //check if fork4 been ceated 
    		{result4 = fork4.join();} //wait for join on fork4
    	
    	if (result4 != null) {return result4;}//check if fork4's result has heartFound
    	//neither you nor your children found the heart, therefore we return null.
    	return null;
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
