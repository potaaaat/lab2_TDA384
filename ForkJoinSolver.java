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
    
    public ForkJoinSolver(Maze maze, int forkAfter, int startNode, ConcurrentSkipListSet<Integer> visited, AtomicBoolean heartFound, HashMap<Integer, Integer> predecessor, Stack<Integer> frontier)
    {
        this(maze);
        this.forkAfter = forkAfter;
        this.start = startNode;
        this.visited = visited;
        this.heartFound = heartFound;
        this.predecessor = predecessor; //TODO: maybe make it local again? unsure. frontier gets very overloaded. not seen as a hash map by program
    	this.frontier = frontier;//no work stealing is implemented right now
        
    }
    
    protected void initStructures() {
    	visited = new ConcurrentSkipListSet<Integer>();
    	//atomic boolean to keep track of when we want to terminate all threads when the goal has been found.
    	heartFound = new AtomicBoolean(false); //if a thread has found the goal this value will chnge to true
    	//if true the other threads need to be terminated: TODO the termination function. 
    	predecessor = new HashMap<>(); 
    	frontier = new Stack<>();
    	
    }

    protected ConcurrentSkipListSet<Integer> visited;
    protected int forkAfter = 0;
    protected AtomicBoolean heartFound;
    protected HashMap<Integer, Integer> predecessor;
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
        return parallelSearch();
    }
    

    private List<Integer> parallelSearch()
    {	
    	int player = maze.newPlayer(start);
        // start with start node
    	//Frontier are the nodes not visited/ next boxes to go into
        frontier.push(start); 			//keep start where we have 1 player and put the start box in frontier
        // as long as not all nodes have been processed
        while (!frontier.empty()) { //not all nodes have been explored  TODO: if forks are added and can work steal the frontier is not uppdated fast enough
        	if(heartFound.get()==true) {break;} //going for a full join if the heart is found
        	if(frontier.size() < forkAfter ) {//condition if maze is forked enough
	            // get the new node to process
        		int current = frontier.pop();
	            //System.out.println(frontier);	PROBLEM SEARCHING            
		            // if current node has a goal
		            if (maze.hasGoal(current)) { //add a func to cancel all other threads if goal is found
		                // move player to goal
		                maze.move(player, current);
		                // search finished: reconstruct and return path
		                heartFound.set(true);
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
		                    frontier.push(nb);
		                    // if nb has not been already visited,
		                    // nb can be reached from current (i.e., current is nb's predecessor)
		                    if (!visited.contains(nb))
		                        predecessor.put(nb, current);
		                }
		                
		            }
		        }                    	
        	else {
        	ForkJoinSolver anotherPath = new ForkJoinSolver(this.maze, this.forkAfter, frontier.pop(), this.visited, this.heartFound, this.predecessor, this.frontier);
        	anotherPath.fork();
        	//We want the parent to idealy continue working. So adding 2 threads is not ideal, I think aiming for 1 added thread is better
        	//anotherPath.join();
        	
        	}
        	
        //if no more nb the thread should be terminated
        // all nodes explored, no goal found}
        	}
        //BIGGEST PROBLEM, might want to do a underfunction. Forks need to exit out of the program to join again, once out of the while they stop any possibilities of joining because of the return
        //join(); //could we use fork join pool?
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
