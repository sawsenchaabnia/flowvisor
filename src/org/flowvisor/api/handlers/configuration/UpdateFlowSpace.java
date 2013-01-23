package org.flowvisor.api.handlers.configuration;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import org.flowvisor.api.APIUserCred;
import org.flowvisor.api.handlers.ApiHandler;
import org.flowvisor.api.handlers.HandlerUtils;
import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;
import org.flowvisor.config.FVConfigurationController;
import org.flowvisor.config.FlowSpace;
import org.flowvisor.config.FlowSpaceImpl;
import org.flowvisor.exceptions.FlowEntryNotFound;
import org.flowvisor.exceptions.MissingRequiredField;
import org.flowvisor.flows.FlowEntry;
import org.flowvisor.flows.FlowMap;
import org.flowvisor.flows.FlowSpaceUtil;
import org.flowvisor.flows.SliceAction;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.openflow.protocol.FVMatch;
import org.openflow.protocol.action.OFAction;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParamsType;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class UpdateFlowSpace implements ApiHandler<List<Map<String, Object>>> {

	
	
	@Override
	public JSONRPC2Response process(final List<Map<String, Object>> params) {
		JSONRPC2Response resp = null;
		try {
			/*
			 * TODO: Add java future here.
			 */
			final FlowMap flowSpace = FVConfig.getFlowSpaceFlowMap();
			final List<FlowEntry> list = processFlows(params, flowSpace);
			FutureTask<Object> future = new FutureTask<Object>(
	                new Callable<Object>() {
	                    public Object call() {
							
	                    	for (FlowEntry fe : list)
	                    		updateFlowEntry(flowSpace, fe);
							FVLog.log(LogLevel.INFO, null,
									"Signalling FlowSpace Update to all event handlers");
							FlowSpaceImpl.getProxy().notifyChange(flowSpace);
							return null;
	                    }
	                });
	                    
			FVConfigurationController.instance().execute(future);	
			resp = new JSONRPC2Response(true, 0);
		} catch (ClassCastException e) {
			resp = new JSONRPC2Response(new JSONRPC2Error(JSONRPC2Error.INVALID_PARAMS.getCode(), 
					cmdName() + ": " + e.getMessage()), 0);
		} catch (MissingRequiredField e) {
			resp = new JSONRPC2Response(new JSONRPC2Error(JSONRPC2Error.INVALID_PARAMS.getCode(), 
					cmdName() + ": " + e.getMessage()), 0);
		} catch (ConfigError e) {
			resp = new JSONRPC2Response(new JSONRPC2Error(JSONRPC2Error.INTERNAL_ERROR.getCode(), 
					cmdName() + ": failed to insert flowspace entry" + e.getMessage()), 0);
		} catch (FlowEntryNotFound e) {
			resp = new JSONRPC2Response(new JSONRPC2Error(JSONRPC2Error.INTERNAL_ERROR.getCode(), 
					cmdName() + "Unable to find flowspace entry :" + e.getMessage()), 0);
			
		}
		return resp;
		
	}

	private List<FlowEntry> processFlows(List<Map<String, Object>> params, FlowMap flowSpace) 
			throws ClassCastException, MissingRequiredField, ConfigError, FlowEntryNotFound {
		String name = null;
		Long dpid = null;
		Integer priority = null;
		FlowEntry update = null;
		LinkedList<FlowEntry> list = new LinkedList<FlowEntry>();
		String logMsg = APIUserCred.getUserName() + " updated";
		for (Map<String,Object> fe : params) {
			name = HandlerUtils.<String>fetchField(FSNAME, fe, false, null);
			if (name == null)
				throw new MissingRequiredField("Cannot update flowspace entry without a name.");
			update = flowSpace.findRuleByName(name);
		
			
			String dpidStr = HandlerUtils.<String>fetchField(FlowSpace.DPID, fe, false, null);
			if (dpidStr != null) {
				dpid = FlowSpaceUtil.parseDPID(dpidStr);
				update.setDpid(dpid);
				logMsg += " dpid="+dpidStr;
			}
			
			priority = HandlerUtils.<Integer>fetchField(FlowSpace.PRIO, fe, false, null);
			if (priority != null) {
				update.setPriority(priority);
				logMsg += " priority="+priority;
			}
			
			
			FVMatch match = HandlerUtils.matchFromMap(
					HandlerUtils.<Map<String, Object>>fetchField(MATCH, fe, false, null));
			if (match != null) {
				update.setRuleMatch(match);
				logMsg += " match=" + match;
			}
			
			
			List<Map<String,Object>> sacts = 
					HandlerUtils.<List<Map<String, Object>>>fetchField(SLICEACTIONS, fe, true, null);
			if (sacts != null) {
				update.setActionsList(parseSliceActions(sacts));
				logMsg += " actions=" + sacts;
			}
			
			list.add(update);
			//updateFlowEntry(flowSpace, update);
			
			FVLog.log(LogLevel.DEBUG, null, logMsg);
		}
		return list;
		
	}
	
	

	private void updateFlowEntry(FlowMap flowSpace, FlowEntry update) {
		try {
			flowSpace.removeRule(update.getId());
			FlowSpaceImpl.getProxy().removeRule(update.getId());
			FlowSpaceImpl.getProxy().addRule(update);
			flowSpace.addRule(update);
		} catch (FlowEntryNotFound e) {
			FVLog.log(LogLevel.WARN, null, "Unable to find flowEntry ", update);
		} catch (ConfigError e) {
			FVLog.log(LogLevel.WARN, null, e.getMessage());
		}
	}

	

	private List<OFAction> parseSliceActions(List<Map<String, Object>> sactions) 
			throws ClassCastException, MissingRequiredField {
		List<OFAction> sa = new LinkedList<OFAction>();
		for (Map<String, Object> sact : sactions) {
			SliceAction sliceAction = new SliceAction(
					HandlerUtils.<String>fetchField(SLICENAME, sact, true, null),
					HandlerUtils.<Number>fetchField(PERM, sact, true, null).intValue());
			sa.add(sliceAction);
		}
		return sa;
	}

	@Override
	public JSONRPC2ParamsType getType() {
		return JSONRPC2ParamsType.ARRAY;
	}

	@Override
	public String cmdName() {
		return "update-flowspace";
	}
	
	

}
