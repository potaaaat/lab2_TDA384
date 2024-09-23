package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

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
    
    protected voit initStructures() {
    	visited = new concurrentSkipListSet<int>();
    	//atomic boolean
    	heartFound = new AtomicBoolean(boolean false); //if a thread has found the goal this value will chnge to true
    	//if true the other threads need to be terminated: TODO the termination function. 
    	predecessor = new HashMap<>();
    	frontier = new Stack<>();
    	
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
    }

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
        while (!frontier.empty()) { //not all nodes have been explored
            // get the new node to process
            int current = frontier.pop();
            // if current node has a goal
            if (maze.hasGoal(current)) { //add a func to cancel all other threads if goal is found
                // move player to goal
                maze.move(player, current);
                // search finished: reconstruct and return path
                return pathFromTo(start, current);
            }
            // if current node has not been visited yet
            if (!visited.contains(current)) { //visited == nodes already checked
                // move player to current node
                maze.move(player, current);
                // mark node as visited
                visited.add(current);
                // for every node nb adjacent to current
                for (int nb: maze.neighbors(current)) {//need to create threads here
                    // add nb to the nodes to be processed
                    frontier.push(nb);
                    
                    if (frontier.length() > 1) {
                    	maze.newPlayer()
                    }
                    // if nb has not been already visited,
                    // nb can be reached from current (i.e., current is nb's predecessor)
                    if (!visited.contains(nb))
                        predecessor.put(nb, current);
                }
                //if no more nb the thread should be terminated
            }
        }
        // all nodes explored, no goal found
        return null;
    }
}
