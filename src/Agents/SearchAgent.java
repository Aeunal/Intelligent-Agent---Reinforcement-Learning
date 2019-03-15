package Agents;
/**
 *  Strategy Engine for Programming Intelligent Agents (SEPIA)
    Copyright (C) 2012 Case Western Reserve University

    This file is part of SEPIA.

    SEPIA is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SEPIA is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SEPIA.  If not, see <http://www.gnu.org/licenses/>.
 */
//package edu.cwru.sepia.agent;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionFeedback;
import edu.cwru.sepia.action.ActionResult;
import edu.cwru.sepia.environment.model.history.BirthLog;
import edu.cwru.sepia.environment.model.history.DamageLog;
import edu.cwru.sepia.environment.model.history.DeathLog;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Template;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.ResourceNode.Type;
import edu.cwru.sepia.environment.model.state.Template.TemplateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;
import edu.cwru.sepia.util.DistanceMetrics;
import edu.cwru.sepia.util.Logger;
import edu.cwru.sepia.agent.Agent;

public class SearchAgent extends Agent{
	private static final long serialVersionUID = 1L;
	
	/**
	 * A list of units by id that also contains orders given to each unit
	 */
	private Map<Integer, Action> unitOrders;
	/**
	 * The player numbers that this guy attacks
	 */
	private int[] enemies;
	private boolean attackEnemyTownhall;	
	private int lastStepMovedIn;
	
	public SearchAgent(int playernum, String[] otherargs) {
		super(playernum);
		if (otherargs == null || otherargs.length == 0)
		{
			setDefaults();
		}
		else
		{
			//copy the list of enemies
			this.verbose = Boolean.parseBoolean(otherargs[2]);
			String[] enemystrs = otherargs[0].split(" ");
			this.enemies = new int[enemystrs.length];
			for (int i = 0; i<enemies.length;i++) {
				this.enemies[i] = Integer.parseInt(enemystrs[i]);
			}
			this.attackEnemyTownhall = Boolean.parseBoolean(otherargs[1]);
		}
	}
	/**
	 * Construct a combat agent.
	 * @param playerNum The player number of this agent
	 * @param enemies The player numbers of enemies
	 * @param wanderWhileIdle Whether to move in random directions when you have nothing better to do.
	 * @param verbose Verbosity 
	 */
	public SearchAgent(int playerNum, int[] enemies, boolean wanderWhileIdle, boolean verbose) {
		super(playerNum);
		this.enemies=new int[enemies.length];
		System.arraycopy(enemies, 0, this.enemies, 0, enemies.length);
		this.attackEnemyTownhall=wanderWhileIdle;
		this.verbose = verbose;
	}
	
	public SearchAgent(int playerNum) {
		super(playerNum);
		setDefaults();
	}
	
	/**
	 * Set the parameters to the default values.
	 * For enemies, they cannot be immediately set, so leave them as null to be interpreted later.
	 */
	private void setDefaults()
	{
		verbose = false;
		attackEnemyTownhall = true;
		enemies = null;
	}
	/**
	 * Start a new trial.
	 * Uses the StateView, which contains information in logs, resources, and units
	 * Some of the unit information may be in the template
	 * @param newstate
	 * @return
	 */
	@Override
	public Map<Integer, Action> initialStep(StateView newstate, History.HistoryView statehistory) {
		//Do setup things for a new game
		/*
		for (Map.Entry<Integer, Action> order : unitOrders.entrySet()) {
			if (order.getValue() == null) //if it has no orders  
			{
				//check all of the other units to check for an enemy that is in sight range
				//int ux = u.getXPosition();
				//int uy = u.getYPosition();
				Direction direction = Direction.values()[(int)(Math.random()*Direction.values().length)];
				//int newx = ux+direction.xComponent();
				//int newy = uy+direction.yComponent();
				Action a = Action.createPrimitiveMove(u.getID(), direction);
				unitOrders.put(order.getKey(), a);
			}
		}
		*/
		//if no enemies were set, then everyone else is the enemy
		if (enemies == null)
		{
			//actually count the enemies, just in case there is some kind of duplicate or if your player number isn't there
			int numenemies = 0;
			for (Integer i : newstate.getPlayerNumbers())
			{
				if (i!=getPlayerNumber())
				{
					numenemies++;
				}
			}
			enemies = new int[numenemies];
			int itr = 0;
			for (Integer i : newstate.getPlayerNumbers())
			{
				if (i!=getPlayerNumber())
				{
					enemies[itr++]=i;
				}
			}
		}
		
			//Clear the unit orders
			unitOrders = new HashMap<Integer, Action>();
			//Put all of the units into the orders.
			for (Integer uid : newstate.getUnitIds(playernum)) {
				unitOrders.put(uid, null);
			}
			initialization(newstate);
			doAggro(newstate);
		Map<Integer, Action> myAction = getAction(newstate);
		lastStepMovedIn = newstate.getTurnNumber();
		return myAction;
	}

	@Override
	public Map<Integer, Action> middleStep(StateView newstate, History.HistoryView statehistory) {
		
		//Read in the logs for every step that occurred since it was last this player's turn
		for (int stepToRead = lastStepMovedIn; stepToRead < newstate.getTurnNumber(); stepToRead++)
		{
			//update its list of units
			for (BirthLog birth : statehistory.getBirthLogs(stepToRead)) {
				if (playernum == birth.getController()) {
					unitOrders.put(birth.getNewUnitID(), null);
				}
			}
			List<Integer> toRemove = new LinkedList<Integer>();
			List<Integer> toUnorder = new LinkedList<Integer>();
			for (DeathLog death : statehistory.getDeathLogs(stepToRead)) {
				//Check if the dead unit is mine
				if (playernum == death.getController()) 
				{
					toRemove.add(death.getDeadUnitID());
				}
				//check if anyone is attacking the dead unit, and tell them to stop
					for (Map.Entry<Integer, Action> order: unitOrders.entrySet()) {
						
						if (order.getValue()!=null)
						{
							Action attackthedeadunit = Action.createCompoundAttack(order.getKey(), death.getDeadUnitID());
							if (attackthedeadunit.equals(order.getValue())) {
								toUnorder.add(order.getKey());
							}
						}
					}
			}
			for (Integer i : toUnorder){
				unitOrders.put(i,null);
			}
			for (Integer i : toRemove) {
				unitOrders.remove(i);
			}
			
			if (verbose)
			{
				//Report the damage dealt by and to your units
				for (DamageLog damagereport : statehistory.getDamageLogs(stepToRead)) {
					if (damagereport.getAttackerController() == playernum) {
						writeLineVisual(damagereport.getAttackerID() + " hit " + damagereport.getDefenderID() + " for " +damagereport.getDamage()+ " damage");
					}
					if (damagereport.getDefenderController() == playernum) {
						writeLineVisual(damagereport.getDefenderID() + " was hit by " + damagereport.getAttackerID() + " for " +damagereport.getDamage()+ " damage");
					}
					
				}
			}
			//Update it's list of orders by checking for completions and failures and removing those
			for (ActionResult feedback : statehistory.getCommandFeedback(playernum, stepToRead).values())
			{
				
				if (feedback.getFeedback() != ActionFeedback.INCOMPLETE)//Everything but incomplete is some form of failure or complete
				{
					//because the feedback mixes primitive feedback on duratives and compound feedback on primitives, need to check if it is the right action
					Action action = feedback.getAction();
					int unitid = action.getUnitId();
					Action order = unitOrders.get(unitid);		//if this gives nullpointer, then there was some failure in registering units with unitOrders
					//check if the completion is the same level as the order
					if (action.equals(order))
					{
						//remove the order, as it is complete or failed
						unitOrders.put(unitid, null);
					}
				}
			}
		}
		//Calculate what the orders should be
		doAggro(newstate);
		
		lastStepMovedIn = newstate.getTurnNumber();
		return getAction(newstate);
	}

	@Override
	public void terminalStep(StateView newstate, History.HistoryView statehistory) {
		//A non learning agent needn't do anything at the final step
		lastStepMovedIn = newstate.getTurnNumber();
	}
	
	
	private Map<Integer, Action> getAction(StateView currentstate) {
		Map<Integer, Action> actions = new HashMap<Integer, Action>();
		for (Map.Entry<Integer, Action> order : unitOrders.entrySet()) {
			if (verbose)
				writeLineVisual("Combat Agent for plr "+playernum+"'s order: " + order.getKey() + " is to use " + order.getValue());
			if (order.getValue() != null) //if it has an order
			{
				//Assign the unit its action
				//actions.put(order.getKey(), Action.createPrimitiveMove(0, Direction.EAST));//
				actions.put(order.getKey(), order.getValue());
			}

		}
		return actions;
	}
	private void doAggro(StateView state) {
		for (Map.Entry<Integer, Action> order : unitOrders.entrySet()) {
			if (order.getValue() == null) //if it has no orders  
			{
				//check all of the other units to check for an enemy that is in sight range
				UnitView u = state.getUnit(order.getKey());
				int ux = u.getXPosition();
				int uy = u.getYPosition();
				int sightradius = u.getTemplateView().getSightRange();
				boolean foundsomething = false;
				for (int enemy : enemies) {
					
					for (Integer enemyUnitID : state.getUnitIds(enemy)) {
						UnitView enemyUnit = state.getUnit(enemyUnitID);
						//get the chebyshev distance (which is the base distance for warcraft 2)
						if (sightradius > DistanceMetrics.chebyshevDistance(ux, uy, enemyUnit.getXPosition(), enemyUnit.getYPosition()) ) {
							//(if you can see it)
							foundsomething=true;
							unitOrders.put(order.getKey(), Action.createCompoundAttack(order.getKey(), enemyUnitID));
							break;
						}
					}
					if (foundsomething)
						break;
				}
				
				if (!foundsomething) {
					//couldn't find an enemy, so wander maybe
					if (attackEnemyTownhall) {
						/*
							Direction direction = Direction.values()[(int)(Math.random()*Direction.values().length)];
							int newx = ux+direction.xComponent();
							int newy = uy+direction.yComponent();
							Action a = Action.createCompoundMove(u.getID(), newx, newy);
							unitOrders.put(order.getKey(), a);
						*/
						step(state, u, order);
					}
				}
				
			}
		}
	}

	public static String getUsage() {
		
		return "It takes three parameters (--agentparam): a space seperated array of enemy player numbers, a boolean for whether it should go and attack to enemy townhall, and a boolean for verbosity";
	}
	@Override
	public void savePlayerData(OutputStream os) {
		//this agent lacks learning and so has nothing to persist.
		
	}
	@Override
	public void loadPlayerData(InputStream is) {
		//this agent lacks learning and so has nothing to persist.
	}
	
	/*
	 * This is the main code 
	 */
	
	Graph g;
	
	// Tree structure
	class Graph {
		private Node start, end;
		private int width, height;
		private boolean noPath = false;
		private int[][] totalCost, gCost;
		private boolean[][] visited, obstacle, temp;
		private Node[][] path;
		private ArrayList<Node> way = new ArrayList<Node>();
		
		public Graph(int w, int h) {
			this.width = w;
			this.height = h;
			visited 	= new boolean[w][h];
			obstacle 	= new boolean[w][h];
			temp 		= new boolean[w][h];
			gCost 		= new int[w][h];
			totalCost	= new int[w][h];
			path 		= new Node[w][h];
			for(int x = 0; x < w; x++) {
				for(int y = 0; y < h; y++) {
					obstacle[x][y] 	= false;
					visited[x][y] 	= false;
					temp[x][y] 		= false;
					totalCost[x][y] = Integer.MAX_VALUE;
					gCost[x][y] 	= Integer.MAX_VALUE;
					path[x][y] 		= new Node(x, y);
				}
			}
		}
		
		class Node {
			private int x, y;
			private Node prev;
			public Node(int x, int y) {
				this.x=x; this.y=y;
			}
			public void setPrev(Node n) {
				this.prev = n;
			}
		}
		
		// initialization methods
		
		public void setStart(int startX, int startY) {
			this.start = new Node(startX, startY);
			visited[start.x][start.y] = true;
			gCost[start.x][start.y] = 0;
		}
		public void setEnd(int endX, int endY) {
			this.end = new Node(endX, endY);
		}
		public void setObstacles(List<Node> list) {
			for(Node t: list) {
				obstacle[t.x][t.y] = true;
			}
		}
		public void addObstacle(int x, int y) {
			obstacle[x][y] = true;
		}
		
		// heuristic formula
		private int getHeuristicCost(int x, int y) {
			return Math.max(Math.abs(end.x - x), Math.abs(end.y - y));
		}
		
		// updating costs
		private void updateCosts(Node n) {
			for(int x = n.x - 1; x < n.x + 2; x++)
				for(int y = n.y - 1; y < n.y + 2; y++) {
					if(x < 0 || y < 0 || x >= width || y >= height) continue;
					if(obstacle[x][y]) continue;
					if(!(n.x == x && n.y == y)) {
						if(gCost[n.x][n.y] < gCost[x][y]) {
							// add current node to next node as previous node
							path[x][y].prev = path[n.x][n.y];
							gCost[x][y] = gCost[n.x][n.y] + 1;
						}
					}
					totalCost[x][y] = gCost[x][y] + getHeuristicCost(x, y);
				}
			
			printArray(totalCost);
		}
		
		// for debugging purposes
		private void printArray(int[][] arr) {
			String format = "%-4s", label = "_____MAP_____";
			System.out.println(label + label + label + label + label);
			for (int j = 0; j < arr[0].length; j++) {
				for(int i = 0; i < arr.length; i++) {
					if(arr[i][j] < Integer.MAX_VALUE) {
						System.out.printf(format,(arr[i][j] + (visited[i][j] ? "< " : "  ")));
					}
					else if(obstacle[i][j]) System.out.printf(format," T ");
					else System.out.printf(format,"   ");
				}
				System.out.println();
			}
			//System.out.println();
		}
		
		// starting to search from the minimum costed node
		private Node minCostNode() {
			Node ret = start;
			int costTemp = Integer.MAX_VALUE;
			for(int x=0; x<width; x++)
				for(int y=0; y<height; y++) {
					if(visited[x][y]) continue;
					if(costTemp > totalCost[x][y]) {
						ret = path[x][y];
						costTemp = totalCost[x][y];
					}
				}
			return ret;
		}
		
		// main A Star Algorithm method
		public void aStarAlgorithm() {
			start.prev = null;
			recursiveAStar(start);
		}
		
		// recursive A Star Algorithm method
		private  void recursiveAStar(Node n) {
			// if it can not find any path
			if(noPath) {
				errorMessage();
				return;
			}
			
			// if the path found
			if(n.x==end.x && n.y == end.y) {
				while(!n.equals(start)) {
					way.add(n);
					n = n.prev;
				}
				succesMessage();
				return;
			}
			
			// check if the algorithm could not make any new visits then path cannot found.
			if(temp[n.x][n.y]) noPath = true;
			if(visited[n.x][n.y]) temp[n.x][n.y] = true;
			
			// visit current node
			visited[n.x][n.y] = true;
			path[n.x][n.y] = n;
			
			// update costs
			updateCosts(n);
			
			// continue recursive with the new minimum costed discovered node
			recursiveAStar(minCostNode());
		}
		
		private void errorMessage() {
			System.out.println("No available path.");
		}
		
		private void succesMessage() {
			System.out.println("Shortest path found.");
		}
		
		/*
		 * Start from the found node and continue with the saved previous nodes
		 */
		public Direction dir(int x, int y) {
			if(way.size()<=0) return null;
			Node target = way.remove(way.size()-1);
			
			// direction calculations
			int d = x-target.x + (y-target.y)*3;
			switch (d) {
			case -4:
				return Direction.SOUTHEAST;
			case -3:
				return Direction.SOUTH;
			case -2:
				return Direction.SOUTHWEST;
			case -1:
				return Direction.EAST;
			case 0:
				return null;
			case 1:
				return Direction.WEST;
			case 2:
				return Direction.NORTHEAST;
			case 3:
				return Direction.NORTH;
			case 4:
				return Direction.NORTHWEST;
			default:
				return null;
			}
		}
	}
	
	
	public void initialization(StateView state) {
		// get UnitViews to find coordinates of units
		UnitView player = null, enemy = null;
		for(UnitView unit : state.getAllUnits()) {
			if(unit.getTemplateView().getName().equals("Footman"))
				player = unit;
			if(unit.getTemplateView().getName().equals("TownHall"))
				enemy = unit;
		}
		debugSize(state);
		debugPos(player);
		debugPos(enemy);
		
		// Create graph
		g = new Graph(state.getXExtent(), state.getYExtent());
		g.setStart(player.getXPosition(), player.getYPosition());
		g.setEnd(enemy.getXPosition(), enemy.getYPosition());
		
		List<Integer> resource = state.getResourceNodeIds(Type.TREE);
		for(Integer tree : resource) {
			ResourceView t = state.getResourceNode(tree);
			debugPos(t);
			g.addObstacle(t.getXPosition(), t.getYPosition());
		}
		
		g.aStarAlgorithm();
	}
	
	/*
	 *  Run this method every step to approach enemy Town Hall
	 */
	public void step(StateView state, UnitView u, Map.Entry<Integer, Action> order) {
		UnitView player = null;
		for(UnitView unit : state.getAllUnits()) {
			if(unit.getTemplateView().getName().equals("Footman")) {
				player = unit;
			}
		}
		Direction newDir = g.dir(player.getXPosition(), player.getYPosition());
		if(newDir != (null)) {
			Action a = Action.createPrimitiveMove(u.getID(), newDir);
			unitOrders.put(order.getKey(), a);
		}
	}
	
	// For debugging purposes
	public void debugPos(UnitView unit) {
		System.out.println(unit.getTemplateView().getName()+"- x:"+unit.getXPosition()+" y:"+unit.getYPosition());
	}
	public void debugPos(ResourceView unit) {
		System.out.println(unit.getType()+"- x:"+unit.getXPosition()+" y:"+unit.getYPosition());
	}
	public void debugSize(StateView state) {
		System.out.println("Map: width-"+state.getXExtent()+" height-"+ state.getYExtent());
	}
	
}
