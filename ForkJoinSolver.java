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
        this.start = start;
        frontier = new ConcurrentLinkedDeque<Integer>();
        
    }

    
    protected void initStructures() {
    	visited = new ConcurrentSkipListSet<Integer>();
    	//atomic boolean to keep track of when we want to terminate all threads when the goal has been found.
    	heartFound = new AtomicBoolean(false); //if a thread has found the goal this value will chnge to true
    	//if true the other threads need to be terminated: TODO the termination function. 
    	predecessor = new ConcurrentHashMap<>();
    	frontier = new ConcurrentLinkedDeque<Integer>();
    	beginning = this.start;
    	
    }
    protected int player;
    protected ConcurrentSkipListSet<Integer> visited;
    protected int forkAfter = 2;
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
		// start with start node
    	//Frontier are the nodes not visited/ next boxes to go into
        ForkJoinSolver fork1 = null;
        ForkJoinSolver fork2 = null;      
//        ForkJoinSolver fork3 = null;
//        ForkJoinSolver fork4 = null;
    	if(!visited.contains(start)){
    		player = maze.newPlayer(start);
    		frontier.push(start);}
    	System.out.println("babys first block " + frontier);
    	
    	//keep start where we have 1 player and put the start box in frontier
    	while(!frontier.isEmpty()) {
    		if(heartFound.get() == true) {break;}
    		if(frontier.size() > forkAfter) {//we want to split the task!
    			System.out.println("frontierlist: " + frontier);
    			int popper = frontier.pop();
            	if(!visited.contains(popper)) {
            		fork1 = new ForkJoinSolver(this.maze, this.forkAfter, popper, this.visited, this.predecessor, this.heartFound);            	
            		fork1.fork();}
            	popper = frontier.pop();
            	if(!visited.contains(popper)) {
            		fork2 = new ForkJoinSolver(this.maze, this.forkAfter, popper, this.visited, this.predecessor, this.heartFound);
            		fork2.fork();
            		}
//            	popper = frontier.pop();
//            	if(!visited.contains(popper)) {
//            		fork3 = new ForkJoinSolver(this.maze, this.forkAfter, popper, this.visited, this.predecessor, this.heartFound);
//            		fork3.fork();
//            		}
//            	if(!visited.contains(frontier.getFirst())) {
//            		fork4 = new ForkJoinSolver(this.maze, this.forkAfter, frontier.pop(), this.visited, this.predecessor, this.heartFound);
//            		fork4.fork();
//            		}


    		}
    		else {
//    			System.out.println("Trying to move");
    			
	            // get the new node to process
	            int current = frontier.pop();
	            // if current node has a goal
	            if (maze.hasGoal(current)) { //add a func to cancel all other threads if goal is found
	            	heartFound.set(true);
	            	// move player to goal
	                maze.move(player, current);
	                // search finished: reconstruct and return path
	                return pathFromTo(beginning, current);
	            }
	            // if current node has not been visited yet
	            if (visited.add(current)) { //visited == nodes already checked. if current is already in the set visited.add() will return false.
	            	// move player to current node
	            	maze.move(player, current);
	                // mark node as visited
	                //visited.add(current);
	                // for every node nb adjacent to current
	                for (int nb: maze.neighbors(current)) {//need to create threads here
	                    // add nb to the nodes to be processed
	                    if(!visited.contains(nb)) {frontier.push(nb);}
	       
	                    // if nb has not been already visited,
	                    // nb can be reached from current (i.e., current is nb's predecessor)
	                    if (!visited.contains(nb))
	                        predecessor.put(nb, current);
	                }
	                //if no more nb the thread should be terminated
	            }
    	        	    
    		}
    		
    	}
    	System.out.println("before join " + player); 
    	// all nodes explored, no goal found
    	List<Integer> result1 = null;
    	if(fork1 != null) {
    		result1 = fork1.join();}
//    	System.out.println("between join " + player);
    	List<Integer> result2 = null;
    	if(fork2 != null) {result2 = fork2.join();}
//    	System.out.println("after join " + player);
    	
//    	List<Integer> result3 = null;
//    	if(fork3 != null) {result3 = fork3.join();}
//    	
//    	List<Integer> result4 = null;
//    	if(fork4 != null) {result4 = fork4.join();}
    	
    	if (result1 != null) {
    		return result1;
    	}
    	if (result2 != null) {
    		return result2;
    	}
//    	if (result3 != null) {
//    		return result3;
//    	}
//    	if (result4 != null) {
//    		return result4;
//    	}
    	
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
