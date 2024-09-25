package amazed.solver;

import amazed.maze.Maze;


import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ConcurrentLinkedDeque;
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

    public ForkJoinSolver(Maze maze, int forkAfter, int startNode, ConcurrentSkipListSet<Integer> visited, AtomicBoolean heartFound)
    {
        this(maze);
        this.forkAfter = forkAfter;
        this.start = startNode;
        this.visited = visited;
        this.heartFound = heartFound;
        this.predecessor = new HashMap<>();//new predesseor and frontier because different players are walking different paths
    	this.frontier = new Stack<>();//no work stealing is implemented right now
        
    }
    
    protected void initStructures() {
    	visited = new ConcurrentSkipListSet<Integer>();
    	availablePaths = new ConcurrentLinkedDeque<Integer>(); 
    	availablePaths.add(start);
    	//atomic boolean to keep track of when we want to terminate all threads when the goal has been found.
    	heartFound = new AtomicBoolean(false); //if a thread has found the goal this value will chnge to true
    	//if true the other threads need to be terminated: TODO the termination function. 
    	predecessor = new HashMap<>();
    	frontier = new Stack<>();
    	
    }

    protected ConcurrentSkipListSet<Integer> visited;
    protected ConcurrentLinkedDeque<Integer> availablePaths;
    protected int forkAfter = 0;
    protected AtomicBoolean heartFound;
    protected Map<Integer, Integer> predecessor;
    protected Stack<Integer> frontier;
    
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
    	 	
    	while(heartFound.get() == false) {
        	if(availablePaths.size() > 1){//change 1 later to forkafter
        		int startNodeForNewPlayer = availablePaths.pop();
	        	ForkJoinSolver fork = new ForkJoinSolver(this.maze, this.forkAfter, startNodeForNewPlayer, this.visited, this.heartFound);
	        	fork.fork();
        	}
        	else {return parallelSearch();}
	        	
        }
  	return null;
    }
    

    private List<Integer> parallelSearch()
    {
    	int player = maze.newPlayer(start);
        // start with start node
    	//Frontier are the nodes not visited/ next boxes to go into
        frontier.push(start); 			//keep start where we have 1 player and put the start box in frontier
        // as long as not all nodes have been processed

        while (!frontier.empty()) { //not all nodes have been explored
            // get the new node to process

        	if(heartFound.get() == true) {
                return null;}//if heartFound == true, then some other thread has found the heart and therefore we can stop executing.
        	
            int current = frontier.pop();
            // if current node has a goal
            if (maze.hasGoal(current)) { //add a func to cancel all other threads if goal is found
                // move player to goal
                maze.move(player, current);
                heartFound.set(true);//we have found the heart so now we have to tell the other threads to stop executing!
                // search finished: reconstruct and return path
                return pathFromTo(start, current);
            }
            // if current node has not been visited yet
            if (visited.add(current)) { //visited == nodes already checked. if current is already in the set visited.add() will return false.
            	// move player to current node
                maze.move(player, current);
                // mark node as visited
                visited.add(current);
  
                // for every node nb adjacent to current
                for (int nb: maze.neighbors(current)) {//need to create threads here
                    // add nb to the nodes to be processed
                	System.out.print(frontier);
                    if(frontier.size()  <= 1) {	frontier.push(nb);}//we want to keep one posible path for ourselves, but if we find more than one available path, we give the rest to others.
                    else {availablePaths.add(nb);}
                                  

//                    if (frontier.size() > 2) {//insdead of 1 - use forkAfter
//                    	int startNodeForNewPlayer = frontier.pop();
//                    	ForkJoinSolver fork = new ForkJoinSolver(this.maze, this.forkAfter, startNodeForNewPlayer, this.visited, this.heartFound);
//                    	fork.fork();
//
//                    }
                    // if nb has not been already visited,
                    // nb can be reached from current (i.e., current is nb's predecessor)
                    if (!visited.contains(nb)) {
                        predecessor.put(nb, current);}
                }
                        
            }
                //if no more nb the thread should be terminated
        }
        
        // all nodes explored, no goal found
        return null;
    }
    
    protected List<Integer> pathFromTo(int from, int to) {//had to put this here, otherwise the program complained that predecessor was Null
        List<Integer> path = new LinkedList<>();
        Integer current = to;
        while (current != from) {
            path.add(current);
            current = predecessor.get(current);
            if (current == null)
                return null;
        }
        path.add(from);
        Collections.reverse(path);
        return path;
    }
    
}
