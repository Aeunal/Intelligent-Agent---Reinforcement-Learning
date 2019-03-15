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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.environment.model.state.ResourceNode.Type;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Template.TemplateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.experiment.Configuration;
import edu.cwru.sepia.experiment.ConfigurationValues;
import edu.cwru.sepia.agent.Agent;

/**
 * This agent will first collect gold to produce a peasant,
 * then the two peasants will collect gold and wood separately until reach goal.
 * @author Feng
 *
 */
public class RCAgent extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final Logger logger = Logger.getLogger(RCAgent.class.getCanonicalName());

	//private int goldRequired;
	//private int woodRequired;
	
	private int step;
	
	public RCAgent(int playernum, String[] arguments) {
		super(playernum);
		
		//goldRequired = Integer.parseInt(arguments[0]);
		//woodRequired = Integer.parseInt(arguments[1]);
	}

	StateView currentState;
	
	@Override
	public Map<Integer, Action> initialStep(StateView newstate, History.HistoryView statehistory) {
		step = 0;
		return middleStep(newstate, statehistory);
	}

	@Override
	public Map<Integer,Action> middleStep(StateView newState, History.HistoryView statehistory) {
		step++;
		if(logger.isLoggable(Level.FINE))
		{
			logger.fine("=> Step: " + step);
		}
		
		Map<Integer,Action> builder = new HashMap<Integer,Action>();
		currentState = newState;
		
		int currentGold = currentState.getResourceAmount(0, ResourceType.GOLD);
		int currentWood = currentState.getResourceAmount(0, ResourceType.WOOD);
		if(logger.isLoggable(Level.FINE))
			logger.fine("Current Gold: " + currentGold);
		if(logger.isLoggable(Level.FINE))
			logger.fine("Current Wood: " + currentWood);
		List<Integer> allUnitIds = currentState.getAllUnitIds();
		List<Integer> peasantIds = new ArrayList<Integer>();
		List<Integer> townhallIds = new ArrayList<Integer>();
		List<Integer> farmIds = new ArrayList<Integer>();
		List<Integer> barrackIds = new ArrayList<Integer>();
		List<Integer> footmanIds = new ArrayList<Integer>();
		List<Integer> enemyIds = new ArrayList<Integer>();
		for(int i=0; i<allUnitIds.size(); i++) {
			int id = allUnitIds.get(i);
			UnitView unit = currentState.getUnit(id);
			int unitPlayer = unit.getTemplateView().getPlayer();
			String unitTypeName = unit.getTemplateView().getName();
			if(unitPlayer == getPlayerNumber()) {
				switch(unitTypeName) {
				case "TownHall"	: townhallIds.add(id); 	break;
				case "Farm"		: farmIds.add(id); 		break;
				case "Barracks"	: barrackIds.add(id); 	break;
				case "Peasant"	: peasantIds.add(id); 	break;
				case "Footman"	: footmanIds.add(id); 	break;
				}
			} else {
				if(unitTypeName.equals("Footman"))
					enemyIds.add(id);
			}
		}
		

		if(peasantIds.size()>=3) {  // collect resources
			if(farmIds.size()<1) {
				if(currentGold >= 500 && currentWood >= 250) {
					//System.out.println("Building Farm");
					UnitView townhallUnit = currentState.getUnit(townhallIds.get(0));
					TemplateView farmTemplate = currentState.getTemplate(playernum, "Farm");
					int farmTemplateID = farmTemplate.getID();
					int peasantId = peasantIds.get(0);
					Action b = Action.createCompoundBuild(peasantId, farmTemplateID,townhallUnit.getXPosition()-1,townhallUnit.getYPosition());
					builder.put(peasantId, b);
				} else {
					if(currentWood<250) {
						int peasantId = peasantIds.get(2);
						int townhallId = townhallIds.get(0);
						Action b = null;
						if(currentState.getUnit(peasantId).getCargoAmount()>0)
							b = new TargetedAction(peasantId, ActionType.COMPOUNDDEPOSIT, townhallId);
						else {
							List<Integer> resourceIds = currentState.getResourceNodeIds(Type.TREE);
							b = new TargetedAction(peasantId, ActionType.COMPOUNDGATHER, resourceIds.get(0));
						}
						builder.put(peasantId, b);
					}
					if(currentGold<500) {
						int townhallId = townhallIds.get(0);
						Action b = null;
						for(int peasantId : peasantIds.subList(0, 2)) {
							if(currentState.getUnit(peasantId).getCargoType() == ResourceType.GOLD && currentState.getUnit(peasantId).getCargoAmount()>0)
								b = new TargetedAction(peasantId, ActionType.COMPOUNDDEPOSIT, townhallId);
							else {
								List<Integer> resourceIds = currentState.getResourceNodeIds(Type.GOLD_MINE);
								b = new TargetedAction(peasantId, ActionType.COMPOUNDGATHER, resourceIds.get(0));
							}
							builder.put(peasantId, b);
						}
					}
				}
			} else if(barrackIds.size()<1) {
				if(currentGold >= 700 && currentWood >= 400) {
					//System.out.println("Building Barrack");
					UnitView townhallUnit = currentState.getUnit(townhallIds.get(0));
					TemplateView barrackTemplate = currentState.getTemplate(playernum, "Barracks");
					int barrackTemplateID = barrackTemplate.getID();
					int peasantId = peasantIds.get(1);
					Action b = Action.createCompoundBuild(peasantId, barrackTemplateID,townhallUnit.getXPosition()+3,townhallUnit.getYPosition());
					builder.put(peasantId, b);
				} else {
					if(currentWood<400) {
						int peasantId = peasantIds.get(2);
						int townhallId = townhallIds.get(0);
						Action b = null;
						if(currentState.getUnit(peasantId).getCargoAmount()>0)
							b = new TargetedAction(peasantId, ActionType.COMPOUNDDEPOSIT, townhallId);
						else {
							List<Integer> resourceIds = currentState.getResourceNodeIds(Type.TREE);
							b = new TargetedAction(peasantId, ActionType.COMPOUNDGATHER, resourceIds.get(0));
						}
						builder.put(peasantId, b);
					}
					if(currentGold<700) {
						int townhallId = townhallIds.get(0);
						Action b = null;
						for(int peasantId : peasantIds.subList(0, 2)) {
							if(currentState.getUnit(peasantId).getCargoType() == ResourceType.GOLD && currentState.getUnit(peasantId).getCargoAmount()>0)
								b = new TargetedAction(peasantId, ActionType.COMPOUNDDEPOSIT, townhallId);
							else {
								List<Integer> resourceIds = currentState.getResourceNodeIds(Type.GOLD_MINE);
								b = new TargetedAction(peasantId, ActionType.COMPOUNDGATHER, resourceIds.get(0));
							}
							builder.put(peasantId, b);
						}
					}
				}
			} else if(footmanIds.size() < 2){
				if(currentGold>=600) {
					//System.out.println("Building Footman");
					TemplateView footmantemplate = currentState.getTemplate(playernum, "Footman");
					int footmantemplateID = footmantemplate.getID();
					int barrackID = barrackIds.get(0);
					builder.put(barrackID, Action.createCompoundProduction(barrackID, footmantemplateID));
				} else {
					int townhallId = townhallIds.get(0);
					Action b = null;
					for(int peasantId : peasantIds) {
						if(currentState.getUnit(peasantId).getCargoType() == ResourceType.GOLD && currentState.getUnit(peasantId).getCargoAmount()>0)
							b = new TargetedAction(peasantId, ActionType.COMPOUNDDEPOSIT, townhallId);
						else {
							List<Integer> resourceIds = currentState.getResourceNodeIds(Type.GOLD_MINE);
							b = new TargetedAction(peasantId, ActionType.COMPOUNDGATHER, resourceIds.get(0));
						}
						builder.put(peasantId, b);
					}
				}
			} else {
				Action b = null;
				for(int footmanID : footmanIds) {
					b = new TargetedAction(footmanID, ActionType.COMPOUNDATTACK, enemyIds.get(0));
					builder.put(footmanID, b);
				}
			}
		} else {  // build peasant
			if(currentGold>=400) {
				//System.out.println("Building peasant");
				if(logger.isLoggable(Level.FINE))
				{
					logger.fine("already have enough gold to produce a new peasant.");
				}
				TemplateView peasanttemplate = currentState.getTemplate(playernum, "Peasant");
				int peasanttemplateID = peasanttemplate.getID();
				if(logger.isLoggable(Level.FINE))
				{
					logger.fine(String.valueOf(peasanttemplate.getID()));
				}
				int townhallID = townhallIds.get(0);
					builder.put(townhallID, Action.createCompoundProduction(townhallID, peasanttemplateID));
			} else {
				//System.out.println("Collecting gold");
				int townhallId = townhallIds.get(0);
				Action b = null;
				for(int peasantId : peasantIds) {
					if(currentState.getUnit(peasantId).getCargoType() == ResourceType.GOLD && currentState.getUnit(peasantId).getCargoAmount()>0)
						b = new TargetedAction(peasantId, ActionType.COMPOUNDDEPOSIT, townhallId);
					else {
						List<Integer> resourceIds = currentState.getResourceNodeIds(Type.GOLD_MINE);
						b = new TargetedAction(peasantId, ActionType.COMPOUNDGATHER, resourceIds.get(0));
					}
					builder.put(peasantId, b);
				}
			}
		}
		return builder;
	}

	@Override
	public void terminalStep(StateView newstate, History.HistoryView statehistory) {
		step++;
		if(logger.isLoggable(Level.FINE))
			logger.fine("=> Step: " + step);
		
		int currentGold = newstate.getResourceAmount(0, ResourceType.GOLD);
		int currentWood = newstate.getResourceAmount(0, ResourceType.WOOD);
		
		if(logger.isLoggable(Level.FINE))
			logger.fine("Current Gold: " + currentGold);
		if(logger.isLoggable(Level.FINE))
			logger.fine("Current Wood: " + currentWood);
		if(logger.isLoggable(Level.FINE))
			logger.fine("Congratulations! You have finished the task!");
	}
	
	public static String getUsage() {
		return "Two arguments, amount of gold to gather and amount of wood to gather";
	}
	@Override
	public void savePlayerData(OutputStream os) {
		//this agent lacks learning and so has nothing to persist.
		
	}
	@Override
	public void loadPlayerData(InputStream is) {
		//this agent lacks learning and so has nothing to persist.
	}
}
