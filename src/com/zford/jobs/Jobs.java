/*
 * Jobs Plugin for Bukkit
 * Copyright (C) 2011  Zak Ford <zak.j.ford@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.zford.jobs;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;


import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.mbertoli.jfep.Parser;

import com.iConomy.iConomy;
import com.nidefawl.Stats.Stats;
import com.nijikokun.bukkit.Permissions.Permissions;
import com.zford.jobs.config.JobsConfiguration;
import com.zford.jobs.config.container.Job;
import com.zford.jobs.config.container.JobProgression;
import com.zford.jobs.config.container.JobsBlockInfo;
import com.zford.jobs.config.container.JobsLivingEntityInfo;
import com.zford.jobs.config.container.PlayerJobInfo;
import com.zford.jobs.economy.JobsBOSEconomyLink;
import com.zford.jobs.economy.JobsiConomy4Link;
import com.zford.jobs.economy.JobsiConomyLink;
import com.zford.jobs.event.JobsJoinEvent;
import com.zford.jobs.event.JobsLeaveEvent;
import com.zford.jobs.fake.JobsPlayer;
import com.zford.jobs.listener.JobsBlockPaymentListener;
import com.zford.jobs.listener.JobsJobListener;
import com.zford.jobs.listener.JobsKillPaymentListener;
import com.zford.jobs.listener.JobsPlayerListener;

import cosine.boseconomy.BOSEconomy;

/**
 * Jobs main class
 * @author Alex
 *
 */
/**
 * @author Zak
 *
 */
public class Jobs extends JavaPlugin{
	
	private HashMap<Player, PlayerJobInfo> players = null;
	
	private static Jobs plugin = null;

	/**
	 * Method called when you disable the plugin
	 */
	public void onDisable() {
		// kill all scheduled tasks associated to this.
		getServer().getScheduler().cancelTasks(this);
		// save all
		if(JobsConfiguration.getInstance().getJobsDAO() != null){
			saveAll();
		}
		
		for(Entry<Player, PlayerJobInfo> online: players.entrySet()){
			// wipe the honorific
			online.getKey().setDisplayName(online.getKey().getDisplayName().replace(online.getValue().getDisplayHonorific(), ""));
		}
		
		getServer().getLogger().info("[Jobs v" + getDescription().getVersion() + "] has been disabled succesfully.");
		// wipe the hashMap
		players.clear();
	}

	/**
	 * Method called when the plugin is enabled
	 */
	public void onEnable() {
		// load the jobConfogiration
		plugin = this;
		players = new HashMap<Player, PlayerJobInfo>();
		JobsConfiguration.getInstance();
		
		if(isEnabled()){
			JobsBlockPaymentListener blockListener = new JobsBlockPaymentListener(this);
			JobsJobListener jobListener = new JobsJobListener(this);
			JobsKillPaymentListener killListener = new JobsKillPaymentListener(this);
			JobsPlayerListener playerListener = new JobsPlayerListener(this);
			
			// set the system to auto save
			if(JobsConfiguration.getInstance().getSavePeriod() > 0){
				getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable(){
					public void run(){
						saveAll();
					}
				}, 20*60*JobsConfiguration.getInstance().getSavePeriod(), 20*60*JobsConfiguration.getInstance().getSavePeriod());
			}
			
			// enable the link for economy plugins
			getServer().getPluginManager().registerEvent(Event.Type.PLUGIN_ENABLE, new ServerListener() {
				
				@Override
				public void onPluginEnable(PluginEnableEvent event) {
									
					// economy plugins
					if(JobsConfiguration.getInstance().getEconomyLink() == null){
						if(getServer().getPluginManager().getPlugin("iConomy") != null || 
								getServer().getPluginManager().getPlugin("BOSEconomy") != null){
							if(getServer().getPluginManager().getPlugin("iConomy") != null){
								if(getServer().getPluginManager().getPlugin("iConomy").getDescription().getVersion().startsWith("4")){
									JobsConfiguration.getInstance().setEconomyLink(
											new JobsiConomy4Link((com.nijiko.coelho.iConomy.iConomy)getServer().getPluginManager().getPlugin("iConomy")));
				                    System.out.println("[Jobs] Successfully linked with iConomy 4.");
								}
								else{
									JobsConfiguration.getInstance().setEconomyLink(new JobsiConomyLink((iConomy)getServer().getPluginManager().getPlugin("iConomy")));
				                    System.out.println("[Jobs] Successfully linked with iConomy 5+.");
								}
							}
							else {
								JobsConfiguration.getInstance().setEconomyLink(new JobsBOSEconomyLink((BOSEconomy)getServer().getPluginManager().getPlugin("BOSEconomy")));
			                    System.out.println("[Jobs] Successfully linked with BOSEconomy.");
							}
						}
					}
					
					// stats
					if(JobsConfiguration.getInstance().getStats() == null && JobsConfiguration.getInstance().isStatsEnabled()){
						if(getServer().getPluginManager().getPlugin("Stats") != null){
							JobsConfiguration.getInstance().setStats((Stats)getServer().getPluginManager().getPlugin("Stats"));
		                    System.out.println("[Jobs] Successfully linked with Stats.");
						}
					}
					
					// permissions
					if(JobsConfiguration.getInstance().getPermissions() == null){
						if(getServer().getPluginManager().getPlugin("Permissions") != null){
							JobsConfiguration.getInstance().setPermissions((Permissions)getServer().getPluginManager().getPlugin("Permissions"));
		                    System.out.println("[Jobs] Successfully linked with Permissions.");
						}
					}
				}
				
				@Override
				public void onPluginDisable(PluginDisableEvent event) {
					if(event.getPlugin().getDescription().getName().equalsIgnoreCase("iConomy") || 
							event.getPlugin().getDescription().getName().equalsIgnoreCase("BOSEconomy")){
						JobsConfiguration.getInstance().setEconomyLink(null);
	                    System.out.println("[Jobs] Economy system successfully unlinked.");
					}
					
					// stats
					if(event.getPlugin().getDescription().getName().equalsIgnoreCase("Stats")){
						JobsConfiguration.getInstance().setStats(null);
	                    System.out.println("[Jobs] Successfully unlinked with Stats.");
					}
					
					// permissions
					if(event.getPlugin().getDescription().getName().equalsIgnoreCase("Permissions")){
						JobsConfiguration.getInstance().setPermissions(null);
	                    System.out.println("[Jobs] Successfully unlinked with Permissions.");
					}
				}
			}, Event.Priority.Monitor, this);
			
			// register the listeners
			getServer().getPluginManager().registerEvent(Event.Type.BLOCK_BREAK, blockListener, Event.Priority.Monitor, this);
			getServer().getPluginManager().registerEvent(Event.Type.BLOCK_PLACE, blockListener, Event.Priority.Monitor, this);
			getServer().getPluginManager().registerEvent(Event.Type.CUSTOM_EVENT, jobListener, Event.Priority.Monitor, this);
			getServer().getPluginManager().registerEvent(Event.Type.ENTITY_DEATH, killListener, Event.Priority.Monitor, this);
			getServer().getPluginManager().registerEvent(Event.Type.PLAYER_JOIN, playerListener, Event.Priority.Monitor, this);
			getServer().getPluginManager().registerEvent(Event.Type.PLAYER_QUIT, playerListener, Event.Priority.Monitor, this);
			
			// add all online players
			for(Player online: getServer().getOnlinePlayers()){
				addPlayer(online);
			}
			
			// all loaded properly.
			getServer().getLogger().info("[Jobs v" + getDescription().getVersion() + "] has been enabled succesfully.");
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {
		if(!label.equalsIgnoreCase("jobs")){
		    return true;
		}
		
		if(sender instanceof Player){
			// player only commands
			// join
			if(args.length == 2 && args[0].equalsIgnoreCase("join")){
				String jobName = args[1].trim();
				if(JobsConfiguration.getInstance().getJob(jobName) != null){
					if((JobsConfiguration.getInstance().getPermissions()!= null &&
							JobsConfiguration.getInstance().getPermissions().isEnabled() &&
							JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.join."+jobName))
							||
							((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled()))){
						if(JobsConfiguration.getInstance().getMaxJobs() == null || players.get((Player)sender).getJobs().size() < JobsConfiguration.getInstance().getMaxJobs()){
							getServer().getPluginManager().callEvent(new JobsJoinEvent(
									(Player)sender, JobsConfiguration.getInstance().getJob(jobName)));
							return true;
						}
						else{
							String message = JobsConfiguration.getInstance().getMessage("join-too-many-job");
							if(message == null){
							    message = ChatColor.RED + "You have already joined too many jobs.";
							}
                            Jobs.sendMessageByLine(sender, message);
                            return true;
						}
					}
					else {
						// you do not have permission to join the job
						noPermission(sender);
						return true;
					}
				}
				else{
					// job does not exist
					jobDoesNotExist(sender);
					return true;
				}
			}
			// leave
			else if(args.length >= 2 && args[0].equalsIgnoreCase("leave")){
				String jobName = args[1].trim();
				if(JobsConfiguration.getInstance().getJob(jobName) != null){
					getServer().getPluginManager().callEvent(new JobsLeaveEvent(
							(Player)sender, JobsConfiguration.getInstance().getJob(jobName)));
				}
				else{
					jobDoesNotExist(sender);
				}
				return true;
			}
			// stats
			else if(args.length == 1 && args[0].equalsIgnoreCase("stats")){
				if(getJob((Player)sender).getJobsProgression().size() == 0){
				    {
						String message = JobsConfiguration.getInstance().getMessage("stats-no-job");
						if(message == null){
						    message = ChatColor.RED + "Please join a job first";
						}
                        Jobs.sendMessageByLine(sender, message);
					}
					return true;
				}
				else{
					for(JobProgression tempJobProg: getJob((Player)sender).getJobsProgression()){
						DecimalFormat format = new DecimalFormat("#.##");
						{
							String message = JobsConfiguration.getInstance().getMessage("stats-job");
							if(message == null){
							    message = "";
							    message += "lvl%joblevel% %jobcolour%%jobname%:\n";
							    message += "    Experience: %jobexp% / %jobmaxexp%";
							}
							message = message.replace("%joblevel%", Integer.valueOf(tempJobProg.getLevel()).toString());
							message = message.replace("%jobcolour%", tempJobProg.getJob().getChatColour().toString());
							message = message.replace("%jobname%", tempJobProg.getJob().getName());
							message = message.replace("%jobexp%", format.format(tempJobProg.getExperience()));
							message = message.replace("%jobmaxexp%", format.format(tempJobProg.getMaxExperience()));
                            Jobs.sendMessageByLine(sender, message);
						}
					}
					return true;
				}
			}
			// jobs info <jobname> <break, place, kill>
			else if(args.length >= 2 && args[0].equalsIgnoreCase("info")){
		        Job job = JobsConfiguration.getInstance().getJob(args[1]);
		        String type = "";
		        if(args.length >= 3) {
		            type = args[2];
		        }
		        this.displayJobInfo(sender, job, type);
		        return true;
			}
		}
		if(sender instanceof ConsoleCommandSender || sender instanceof Player){
			// browse
			if(args.length >= 1 && args[0].equalsIgnoreCase("browse")){
				ArrayList<String> jobs = new ArrayList<String>();
				for(Job temp: JobsConfiguration.getInstance().getJobs()){
					if(sender instanceof ConsoleCommandSender || 
							(JobsConfiguration.getInstance().getPermissions()!= null &&
							JobsConfiguration.getInstance().getPermissions().isEnabled() &&
							JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.join."+temp.getName()))
							||
							((JobsConfiguration.getInstance().getPermissions() == null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled()))){
						if(temp.getMaxLevel() == null){
							jobs.add(temp.getChatColour() + temp.getName());
						}
						else{
							jobs.add(temp.getChatColour() + temp.getName() + ChatColor.WHITE + " - max lvl: " + temp.getMaxLevel());
						}
					}
				}
				if(jobs.size() == 0){
					String message = JobsConfiguration.getInstance().getMessage("browse-no-jobs");
					if(message == null){
						message = "There are no jobs you can join";
					}
                    Jobs.sendMessageByLine(sender, message);
					
				}
				else{
				    {
						String message = JobsConfiguration.getInstance().getMessage("browse-jobs-header");
						if(message == null){
						    message = "You are allowed to join the following jobs:";
						}
                        Jobs.sendMessageByLine(sender, message);
					}
				    
				    for(String job : jobs) {
				        sender.sendMessage("    "+job);
				    }
				    
					{
						String message = JobsConfiguration.getInstance().getMessage("browse-jobs-footer");
						if(message == null){
						    message = "For more information type in /jobs info [JobName]";
						}
					    Jobs.sendMessageByLine(sender, message);
					}
				}
				return true;
			}
			
			// admin commands
			if(args.length == 3){
				if(args[0].equalsIgnoreCase("fire")){
					if(sender instanceof ConsoleCommandSender || 
							(JobsConfiguration.getInstance().getPermissions()!= null &&
							JobsConfiguration.getInstance().getPermissions().isEnabled() &&
							JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.fire"))
							||
							(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
						Player target = getServer().getPlayer(args[1]);
						if(target == null){
							target = new JobsPlayer(args[1]);
						}
						Job job = JobsConfiguration.getInstance().getJob(args[2]);
						if(target != null && job != null){
							try{
								// check if player even has the job
								PlayerJobInfo info = players.get(target);
								if(info == null){
									// player isn't online
									info = new PlayerJobInfo(target, JobsConfiguration.getInstance().getJobsDAO());
								}
								if(info.isInJob(job)){
									getServer().getPluginManager().callEvent(new JobsLeaveEvent(target, job));
									String message = JobsConfiguration.getInstance().getMessage("fire-target");
									if(message == null){
									    message = "You have been fired from " + job.getChatColour() + job.getName();
									}
								    message = message.replace("%jobcolour%", job.getChatColour().toString());
								    message = message.replace("%jobname%", job.getName());
                                    Jobs.sendMessageByLine(target, message);
                                    
                                    Jobs.sendAdminCommandSuccess(sender);
								}
								else{
									String message = JobsConfiguration.getInstance().getMessage("fire-target-no-job");
									if(message == null){
									    message = "Player does not have the job %jobcolour%%jobname%";
									}
									message = message.replace("%jobcolour%", job.getChatColour().toString());
									message = message.replace("%jobname%", job.getName());
                                    Jobs.sendMessageByLine(sender, message);
								}
							}
							catch (Exception e){
                                Jobs.sendAdminCommandFailed(sender);
							}
						}
					}
					return true;
				}
				else if(args[0].equalsIgnoreCase("employ")){
					if(sender instanceof ConsoleCommandSender || 
							(JobsConfiguration.getInstance().getPermissions()!= null &&
							JobsConfiguration.getInstance().getPermissions().isEnabled() &&
							JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.employ."+args[2]))
							||
							(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
						Player target = getServer().getPlayer(args[1]);
						if(target == null){
							target = new JobsPlayer(args[1]);
						}
						Job job = JobsConfiguration.getInstance().getJob(args[2]);
						if(target != null && job != null){
							try{
								// check if player already has the job
								PlayerJobInfo info = players.get(target);
								if(info == null){
									// player isn't online
									info = new PlayerJobInfo(target, JobsConfiguration.getInstance().getJobsDAO());
								}
								if(!info.isInJob(job)){
									getServer().getPluginManager().callEvent(new JobsJoinEvent(target, job));
									String message = JobsConfiguration.getInstance().getMessage("employ-target");
									if(message == null){
									    message = "You have been employed in %jobcolour%%jobname%";
									}
								    message = message.replace("%jobcolour%", job.getChatColour().toString());
								    message = message.replace("%jobname%", job.getName());
                                    Jobs.sendMessageByLine(target, message);
                                    
                                    Jobs.sendAdminCommandSuccess(sender);
								}
							}
							catch (Exception e){
                                Jobs.sendAdminCommandFailed(sender);
							}
						}
					}
				}
				return true;
			}
			else if(args.length == 4){
				if(args[0].equalsIgnoreCase("promote")){
					if(sender instanceof ConsoleCommandSender || 
							(JobsConfiguration.getInstance().getPermissions()!= null &&
							JobsConfiguration.getInstance().getPermissions().isEnabled() &&
							JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.promote"))
							||
							(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
						Player target = getServer().getPlayer(args[1]);
						if(target == null){
							target = new JobsPlayer(args[1]);
						}
						Job job = JobsConfiguration.getInstance().getJob(args[2]);
						if(target != null && job != null){
							try{
								// check if player already has the job
								PlayerJobInfo info = players.get(target);
								if(info == null){
									// player isn't online
									info = new PlayerJobInfo(target, JobsConfiguration.getInstance().getJobsDAO());
								}
								if(info.isInJob(job)){
									Integer levelsGained = Integer.parseInt(args[3]);
									if (info.getJobsProgression(job).getJob().getMaxLevel() != null &&
											levelsGained + info.getJobsProgression(job).getLevel() > info.getJobsProgression(job).getJob().getMaxLevel()){
										levelsGained = info.getJobsProgression(job).getJob().getMaxLevel() - info.getJobsProgression(job).getLevel();
									}
									info.getJobsProgression(job).setLevel(info.getJobsProgression(job).getLevel() + levelsGained);
									if(!(target instanceof JobsPlayer)){
										info.reloadMaxExperience();
										info.checkLevels();
									}
									
									{
										String message = JobsConfiguration.getInstance().getMessage("promote-target");
										if(message == null){
										    message = "You have been promoted %levelsgained% levels in %jobcolour%%jobname%";
										}
									    message = message.replace("%jobcolour%", job.getChatColour().toString());
									    message = message.replace("%jobname%", job.getName());
									    message = message.replace("%levelsgained%", levelsGained.toString());
                                        Jobs.sendMessageByLine(target, message);
									}
                                    Jobs.sendAdminCommandSuccess(sender);
								}
								if(target instanceof JobsPlayer){
									JobsConfiguration.getInstance().getJobsDAO().save(info);
								}
							}
							catch (Exception e){
                                Jobs.sendAdminCommandFailed(sender);
							}
						}
					}
					return true;
				}
				else if(args[0].equalsIgnoreCase("demote")){
					if(sender instanceof ConsoleCommandSender || 
							(JobsConfiguration.getInstance().getPermissions()!= null &&
							JobsConfiguration.getInstance().getPermissions().isEnabled() &&
							JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.demote"))
							||
							(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
						Player target = getServer().getPlayer(args[1]);	
						if(target == null){
							target = new JobsPlayer(args[1]);
						}
						Job job = JobsConfiguration.getInstance().getJob(args[2]);
						if(target != null && job != null){
							try{
								// check if player already has the job
								PlayerJobInfo info = players.get(target);
								if(info == null){
									// player isn't online
									info = new PlayerJobInfo(target, JobsConfiguration.getInstance().getJobsDAO());
								}
								if(info.isInJob(job)){
									Integer levelsLost = Integer.parseInt(args[3]);
									if (info.getJobsProgression(job).getLevel() - levelsLost < 1){
										levelsLost = info.getJobsProgression(job).getLevel() - 1;
									}
									info.getJobsProgression(job).setLevel(info.getJobsProgression(job).getLevel() - levelsLost);
									if(!(target instanceof JobsPlayer)){
										info.reloadMaxExperience();
										info.checkLevels();
									}
									
									{
										String message = JobsConfiguration.getInstance().getMessage("demote-target");
										if(message == null){
										    message = "You have been demoted %levelslost% levels in %jobcolour%%jobname%";
										}
										message = message.replace("%jobcolour%", job.getChatColour().toString());
										message = message.replace("%jobname%", job.getName());
										message = message.replace("%levelslost%", levelsLost.toString());
                                        Jobs.sendMessageByLine(target, message);
									}
                                    Jobs.sendAdminCommandSuccess(sender);
								}
								if(target instanceof JobsPlayer){
									JobsConfiguration.getInstance().getJobsDAO().save(info);
								}
							}
							catch (Exception e){
                                Jobs.sendAdminCommandFailed(sender);
							}
						}
					}
					return true;
				}
				else if(args[0].equalsIgnoreCase("grantxp")){
					if(sender instanceof ConsoleCommandSender || 
							(JobsConfiguration.getInstance().getPermissions()!= null &&
							JobsConfiguration.getInstance().getPermissions().isEnabled() &&
							JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.grantxp"))
							||
							(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
						Player target = getServer().getPlayer(args[1]);
						if(target == null){
							target = new JobsPlayer(args[1]);
						}
						Job job = JobsConfiguration.getInstance().getJob(args[2]);
						if(target != null && job != null){
							Double expGained;
							try{
								expGained = Double.parseDouble(args[3]);
							}
							catch (ClassCastException ex){
								expGained = (double) Integer.parseInt(args[3]);
							}
							catch(Exception e){
                                Jobs.sendAdminCommandFailed(sender);
								return true;
							}
							// check if player already has the job
							PlayerJobInfo info = players.get(target);
							if(info == null){
								// player isn't online
								info = new PlayerJobInfo(target, JobsConfiguration.getInstance().getJobsDAO());
							}
							if(info.isInJob(job)){
								info.getJobsProgression(job).setExperience(info.getJobsProgression(job).getExperience() + expGained);
								if(!(target instanceof JobsPlayer)){
									info.reloadMaxExperience();
									info.checkLevels();
								}
								{
									String message = JobsConfiguration.getInstance().getMessage("grantxp-target");
									if(message == null){
									    message = "You have been granted %expgained% experience in %jobcolour%%jobname%";
									}
									message = message.replace("%jobcolour%", job.getChatColour().toString());
									message = message.replace("%jobname%", job.getName());
									message = message.replace("%expgained%", args[3]);
                                    Jobs.sendMessageByLine(target, message);
								}
                                Jobs.sendAdminCommandSuccess(sender);
							}
							if(target instanceof JobsPlayer){
								JobsConfiguration.getInstance().getJobsDAO().save(info);
							}
						}
					}
					return true;
				}
				else if(args[0].equalsIgnoreCase("removexp")){
					if(sender instanceof ConsoleCommandSender || 
							(JobsConfiguration.getInstance().getPermissions()!= null &&
							JobsConfiguration.getInstance().getPermissions().isEnabled() &&
							JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.removexp"))
							||
							(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
						Player target = getServer().getPlayer(args[1]);
						if(target == null){
							target = new JobsPlayer(args[1]);
						}
						Job job = JobsConfiguration.getInstance().getJob(args[2]);
						if(target != null && job != null){
							Double expLost;
							try{
								expLost = Double.parseDouble(args[3]);
							}
							catch (ClassCastException ex){
								expLost = (double) Integer.parseInt(args[3]);
							}
							catch(Exception e){
                                Jobs.sendAdminCommandFailed(sender);
								return true;
							}
							// check if player already has the job
							PlayerJobInfo info = players.get(target);
							if(info == null){
								// player isn't online
								info = new PlayerJobInfo(target, JobsConfiguration.getInstance().getJobsDAO());
							}
							if(info.isInJob(job)){
								info.getJobsProgression(job).setExperience(info.getJobsProgression(job).getExperience() - expLost);
								
								{
									String message = JobsConfiguration.getInstance().getMessage("removexp-target");
									if(message == null){
									    message = "You have lost %explost% experience in jobcolour%%jobname%";
									}
								    message = message.replace("%jobcolour%", job.getChatColour().toString());
								    message = message.replace("%jobname%", job.getName());
								    message = message.replace("%explost%", args[3]);
                                    Jobs.sendMessageByLine(target, message);
								}
                                Jobs.sendAdminCommandSuccess(sender);
							}
							if(target instanceof JobsPlayer){
								JobsConfiguration.getInstance().getJobsDAO().save(info);
							}
						}
					}
					return true;
				}
				else if(args[0].equalsIgnoreCase("transfer")){
					if(sender instanceof ConsoleCommandSender || 
							(JobsConfiguration.getInstance().getPermissions()!= null &&
							JobsConfiguration.getInstance().getPermissions().isEnabled() &&
							JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.transfer"))
							||
							(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
						Player target = getServer().getPlayer(args[1]);
						if(target == null){
							target = new JobsPlayer(args[1]);
						}
						Job oldjob = JobsConfiguration.getInstance().getJob(args[2]);
						Job newjob = JobsConfiguration.getInstance().getJob(args[3]);
						if(target != null && oldjob != null & newjob != null){
							try{
								PlayerJobInfo info = players.get(target);
								if (info == null){
									info = new PlayerJobInfo(target, JobsConfiguration.getInstance().getJobsDAO());
								}
								if(info.isInJob(oldjob) && !info.isInJob(newjob)){
									info.transferJob(oldjob, newjob);
									if(newjob.getMaxLevel() != null && info.getJobsProgression(newjob).getLevel() > newjob.getMaxLevel()){
										info.getJobsProgression(newjob).setLevel(newjob.getMaxLevel());
									}
									if(!(target instanceof JobsPlayer)){
										info.reloadMaxExperience();
										info.reloadHonorific();
										info.checkLevels();
									}
									// quit old job
									JobsConfiguration.getInstance().getJobsDAO().quitJob(target, oldjob);
									// join new job
									JobsConfiguration.getInstance().getJobsDAO().joinJob(target, newjob);
									// save data
									JobsConfiguration.getInstance().getJobsDAO().save(info);
									{
										String message = JobsConfiguration.getInstance().getMessage("removexp-target");
										if(message == null){
										    message = "You have been transferred from %oldjobcolour%%oldjobname% to %newjobcolour%%newjobname%";
										}
									    message = message.replace("%oldjobcolour%", oldjob.getChatColour().toString());
									    message = message.replace("%oldjobname%", oldjob.getName());
									    message = message.replace("%newjobcolour%", newjob.getChatColour().toString());
										message = message.replace("%newjobname%", newjob.getName());
	                                    Jobs.sendMessageByLine(target, message);
									}
                                    Jobs.sendAdminCommandSuccess(sender);
									// stats plugin integration
									if(JobsConfiguration.getInstance().getStats() != null &&
											JobsConfiguration.getInstance().getStats().isEnabled()){
										Stats stats = JobsConfiguration.getInstance().getStats();
										if(info.getJobsProgression(newjob).getLevel() > stats.get(target.getName(), "job", newjob.getName())){
											stats.setStat(target.getName(), "job", newjob.getName(), info.getJobsProgression(newjob).getLevel());
											stats.saveAll();
										}
									}
								}
							}
							catch (Exception e){
							    Jobs.sendAdminCommandFailed(sender);
							}
						}
					}
				}
				return true;
			}
			if(args.length > 0){
				sender.sendMessage(ChatColor.RED + "There was an error in your command");
			}
			
			{
				// jobs-browse
				String message = JobsConfiguration.getInstance().getMessage("jobs-browse");
				if(message == null){
					sender.sendMessage("/jobs browse - list the jobs available to you");
				}
                Jobs.sendMessageByLine(sender, message);
			}
			
			if(sender instanceof Player){
                {
                    // jobs-join
                    String message = JobsConfiguration.getInstance().getMessage("jobs-join");
                    if(message == null){
                    	sender.sendMessage("/jobs join <jobname> - join the selected job");
                    }
                    Jobs.sendMessageByLine(sender, message);
                }
                
                {
                    //jobs-leave
                    String message = JobsConfiguration.getInstance().getMessage("jobs-leave");
                    if(message == null){
                    	sender.sendMessage("/jobs leave <jobname> - leave the selected job");
                    }
                    Jobs.sendMessageByLine(sender, message);
                }
                
                {
                	//jobs-stats
                    String message = JobsConfiguration.getInstance().getMessage("jobs-stats");
                	if(message == null){
                		sender.sendMessage("/jobs stats - show the level you are in each job you are part of");
                	}
                    Jobs.sendMessageByLine(sender, message);
                }
                
                {
                	//jobs-info
                    String message = JobsConfiguration.getInstance().getMessage("jobs-info");
                	if(message == null){
                		message = "/jobs info <jobname> <break, place, kill> - show how much each job is getting paid and for what";
                	}
                    Jobs.sendMessageByLine(sender, message);
                }
			}
			//jobs-admin-fire
			if(sender instanceof ConsoleCommandSender || 
					(JobsConfiguration.getInstance().getPermissions()!= null &&
					JobsConfiguration.getInstance().getPermissions().isEnabled() &&
					JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.fire"))
					||
					(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
				String message = JobsConfiguration.getInstance().getMessage("jobs-admin-fire");
				if(message == null){
				    message = "/jobs fire <playername> <job> - fire the player from the job";
				}
                Jobs.sendMessageByLine(sender, message);
			}
			//jobs-admin-employ
			if(sender instanceof ConsoleCommandSender || 
					(JobsConfiguration.getInstance().getPermissions()!= null &&
					JobsConfiguration.getInstance().getPermissions().isEnabled() &&
					JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.employ"))
					||
					(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
				String message = JobsConfiguration.getInstance().getMessage("jobs-admin-employ");
				if(message == null){
					message = "/jobs employ <playername> <job> - employ the player to the job";
				}
                Jobs.sendMessageByLine(sender, message);
			}
			//jobs-admin-promote
			if(sender instanceof ConsoleCommandSender || 
					(JobsConfiguration.getInstance().getPermissions()!= null &&
					JobsConfiguration.getInstance().getPermissions().isEnabled() &&
					JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.promote"))
					||
					(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
				String message = JobsConfiguration.getInstance().getMessage("jobs-admin-promote");
				if(message == null){
					message = "/jobs promote <playername> <job> <levels> - promote the player X levels in a job";
				}
				Jobs.sendMessageByLine(sender, message);
			}
			//jobs-admin-demote
			if(sender instanceof ConsoleCommandSender || 
					(JobsConfiguration.getInstance().getPermissions()!= null &&
					JobsConfiguration.getInstance().getPermissions().isEnabled() &&
					JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.demote"))
					||
					(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
			    String message = JobsConfiguration.getInstance().getMessage("jobs-admin-demote");
				if(message == null){
				    message = "/jobs demote <playername> <job> <levels> - demote the player X levels in a job";
				}
				Jobs.sendMessageByLine(sender, message);
			}
			//jobs-admin-grantxp
			if(sender instanceof ConsoleCommandSender || 
					(JobsConfiguration.getInstance().getPermissions()!= null &&
					JobsConfiguration.getInstance().getPermissions().isEnabled() &&
					JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.grantxp"))
					||
					(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
			    String message = JobsConfiguration.getInstance().getMessage("jobs-admin-grantxp");
				if(message == null){
				    message = "/jobs grantxp <playername> <job> <experience> - grant the player X experience in a job";
				}
                Jobs.sendMessageByLine(sender, message);
			}
			//jobs-admin-removexp
			if(sender instanceof ConsoleCommandSender || 
					(JobsConfiguration.getInstance().getPermissions()!= null &&
					JobsConfiguration.getInstance().getPermissions().isEnabled() &&
					JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.removexp"))
					||
					(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
				String message = JobsConfiguration.getInstance().getMessage("jobs-admin-removexp");
				if(message == null){
				    message = "/jobs removexp <playername> <job> <experience> - remove X experience from the player in a job";
				}
                Jobs.sendMessageByLine(sender, message);
			}
			//jobs-admin-transfer
			if(sender instanceof ConsoleCommandSender || 
					(JobsConfiguration.getInstance().getPermissions()!= null &&
					JobsConfiguration.getInstance().getPermissions().isEnabled() &&
					JobsConfiguration.getInstance().getPermissions().getHandler().has((Player)sender, "jobs.admin.transfer"))
					||
					(((JobsConfiguration.getInstance().getPermissions()== null) || !(JobsConfiguration.getInstance().getPermissions().isEnabled())) && sender.isOp())){
				String message = JobsConfiguration.getInstance().getMessage("jobs-admin-transfer");
				if(message == null){
					message = "/jobs transfer <playername> <oldjob> <newjob> - transfer a player's job from an old job to a new job";
				}
                Jobs.sendMessageByLine(sender, message);
			}
		}
		return true;
	}
	
	/**
	 * Displays info about a job
	 * @param sender - who receives info
	 * @param job - the job we are displaying info about
	 * @param type - type of info
	 */
	private void displayJobInfo(CommandSender sender, Job job, String type) {
        if(job == null){
            // job doesn't exist
            jobDoesNotExist(sender);
            return;
        }
        
        int showAllTypes = 1;
        if(type.equalsIgnoreCase("break") || type.equalsIgnoreCase("place") || type.equalsIgnoreCase("kill")) {
            showAllTypes = 0;
        }
        
        if(type.equalsIgnoreCase("break") || showAllTypes == 1){
            // break
            HashMap<String, JobsBlockInfo> jobBreakInfo = job.getBreakInfo();
            
            if(jobBreakInfo != null){
                this.displayJobInfoBreak(sender, job, jobBreakInfo);
            }
            else if(showAllTypes == 0) {
                String message = JobsConfiguration.getInstance().getMessage("break-none");
                if(message == null){
                    message = "%jobcolour%%jobname%" +ChatColor.WHITE+ " does not get money from breaking anything.";
                }
                message = message.replace("%jobcolour%", job.getChatColour().toString());
                message = message.replace("%jobname%", job.getName());
                Jobs.sendMessageByLine(sender, message);
            }
        }
        if(type.equalsIgnoreCase("place") || showAllTypes == 1){
            // place
            HashMap<String, JobsBlockInfo> jobPlaceInfo = job.getPlaceInfo();
            
            if(jobPlaceInfo != null){
                this.displayJobInfoPlace(sender, job, jobPlaceInfo);
            }
            else if(showAllTypes == 0) {
                String message = JobsConfiguration.getInstance().getMessage("place-none");
                if(message == null){
                    message = "%jobcolour%%jobname%" +ChatColor.WHITE+ " does not get money from placing anything.";
                }
                message = message.replace("%jobcolour%", job.getChatColour().toString());
                message = message.replace("%jobname%", job.getName());
                Jobs.sendMessageByLine(sender, message);
            }
        }
        if(type.equalsIgnoreCase("kill") || showAllTypes == 1){
            // kill
            HashMap<String, JobsLivingEntityInfo> jobKillInfo = job.getKillInfo();
            
            if(jobKillInfo != null){
                this.displayJobInfoKill(sender, job, jobKillInfo);
            }
            else if(showAllTypes == 0) {
                String message = JobsConfiguration.getInstance().getMessage("kill-none");
                if(message == null){
                    message = "%jobcolour%%jobname%" +ChatColor.WHITE+ " does not get money from killing anything.";
                }
                message = message.replace("%jobcolour%", job.getChatColour().toString());
                message = message.replace("%jobname%", job.getName());
                Jobs.sendMessageByLine(sender, message);
            }
        }
	}
	
	/**
     * Displays info about breaking blocks
     * @param sender - who receives info
     * @param job - the job we are displaying info about
     * @param jobBreakInfo - the information to display
     */
	private void displayJobInfoBreak(CommandSender sender, Job job, HashMap<String, JobsBlockInfo> jobBreakInfo) {
	    
	    {
            String message = JobsConfiguration.getInstance().getMessage("break-header");
            if(message == null){
                message = "Break:";
            }
            Jobs.sendMessageByLine(sender, message);
	    }
        
        DecimalFormat format = new DecimalFormat("#.##");
        JobProgression prog = players.get((Player)sender).getJobsProgression(job);
        Parser expEquation = job.getExpEquation();
        Parser incomeEquation = job.getIncomeEquation();
        if(prog != null){
            expEquation.setVariable("joblevel", prog.getLevel());
            incomeEquation.setVariable("joblevel", prog.getLevel());
        }
        else {
            expEquation.setVariable("joblevel", 1);
            incomeEquation.setVariable("joblevel", 1);
        }
        expEquation.setVariable("numjobs", players.get((Player)sender).getJobs().size());
        incomeEquation.setVariable("numjobs", players.get((Player)sender).getJobs().size());
        for(Entry<String, JobsBlockInfo> temp: jobBreakInfo.entrySet()){
            expEquation.setVariable("baseexperience", temp.getValue().getXpGiven());
            incomeEquation.setVariable("baseincome", temp.getValue().getMoneyGiven());
            String message;
            if(temp.getKey().contains(":")){
                message = JobsConfiguration.getInstance().getMessage("break-info-sub");
            }
            else {
                message = JobsConfiguration.getInstance().getMessage("break-info-no-sub");
            }
            if(message == null){
                message = "    %item%:%subitem% - %income%" + ChatColor.GREEN + " income" + ChatColor.WHITE + ", %experience%" + 
                    ChatColor.YELLOW + " exp";
            }
            if(temp.getKey().contains(":")){
                message = message.replace("%item%", temp.getKey().split(":")[0].replace("_", " ").toLowerCase());
                message = message.replace("%subitem%", temp.getKey().split(":")[1]);
            }
            else{
                message = message.replace("%item%", temp.getKey().replace("_", " ").toLowerCase());
                message = message.replace(":%subitem%", "");
            }
            message = message.replace("%income%", format.format(incomeEquation.getValue()));
            message = message.replace("%experience%", format.format(expEquation.getValue()));
            Jobs.sendMessageByLine(sender, message);
        }
	}
	
    /**
     * Displays info about placing blocks
     * @param sender - who receives info
     * @param job - the job we are displaying info about
     * @param jobPlaceInfo - the information to display
     */	
	private void displayJobInfoPlace(CommandSender sender, Job job, HashMap<String, JobsBlockInfo> jobPlaceInfo) {
	    
	    {
            String message = JobsConfiguration.getInstance().getMessage("place-header");
            if(message == null){
                message = "Place:";
            }
            Jobs.sendMessageByLine(sender, message);
	    }
        DecimalFormat format = new DecimalFormat("#.##");
        JobProgression prog = players.get((Player)sender).getJobsProgression(job);
        Parser expEquation = job.getExpEquation();
        Parser incomeEquation = job.getIncomeEquation();
        if(prog != null){
            expEquation.setVariable("joblevel", prog.getLevel());
            incomeEquation.setVariable("joblevel", prog.getLevel());
        }
        else {
            expEquation.setVariable("joblevel", 1);
            incomeEquation.setVariable("joblevel", 1);
        }
        expEquation.setVariable("numjobs", players.get((Player)sender).getJobs().size());
        incomeEquation.setVariable("numjobs", players.get((Player)sender).getJobs().size());
        for(Entry<String, JobsBlockInfo> temp: jobPlaceInfo.entrySet()){
            expEquation.setVariable("baseexperience", temp.getValue().getXpGiven());
            incomeEquation.setVariable("baseincome", temp.getValue().getMoneyGiven());
            String message;
            if(temp.getKey().contains(":")){
                message = JobsConfiguration.getInstance().getMessage("place-info-sub");
            }
            else {
                message = JobsConfiguration.getInstance().getMessage("place-info-no-sub");
            }
            if(message == null){
                message = "    %item%:%subitem% - %income%" + ChatColor.GREEN + " income" + ChatColor.WHITE + ", %experience%" + 
                    ChatColor.YELLOW + " exp";
            }
            if(temp.getKey().contains(":")){
                message = message.replace("%item%", temp.getKey().split(":")[0].replace("_", " ").toLowerCase());
                message = message.replace("%subitem%", temp.getKey().split(":")[1]);
            }
            else{
                message = message.replace("%item%", temp.getKey().replace("_", " ").toLowerCase());
                message = message.replace(":%subitem%", "");
            }
            message = message.replace("%income%", format.format(incomeEquation.getValue()));
            message = message.replace("%experience%", format.format(expEquation.getValue()));
            Jobs.sendMessageByLine(sender, message);
        }
	}
	
    /**
     * Displays info about killing entities
     * @param sender - who receives info
     * @param job - the job we are displaying info about
     * @param jobKillInfo - the information to display
     */
    private void displayJobInfoKill(CommandSender sender, Job job, HashMap<String, JobsLivingEntityInfo> jobKillInfo) {
        {
            String message = JobsConfiguration.getInstance().getMessage("kill-header");
            if(message == null){
                message = "Kill:";
            }
            Jobs.sendMessageByLine(sender, message);
        }
        DecimalFormat format = new DecimalFormat("#.##");
        JobProgression prog = players.get((Player)sender).getJobsProgression(job);
        Parser expEquation = job.getExpEquation();
        Parser incomeEquation = job.getIncomeEquation();
        if(prog != null){
            expEquation.setVariable("joblevel", prog.getLevel());
            incomeEquation.setVariable("joblevel", prog.getLevel());
        }
        else {
            expEquation.setVariable("joblevel", 1);
            incomeEquation.setVariable("joblevel", 1);
        }
        expEquation.setVariable("numjobs", players.get((Player)sender).getJobs().size());
        incomeEquation.setVariable("numjobs", players.get((Player)sender).getJobs().size());
        for(Entry<String, JobsLivingEntityInfo> temp: jobKillInfo.entrySet()){
            expEquation.setVariable("baseexperience", temp.getValue().getXpGiven());
            incomeEquation.setVariable("baseincome", temp.getValue().getMoneyGiven());
            String message;
            if(temp.getKey().contains(":")){
                message = JobsConfiguration.getInstance().getMessage("kill-info-sub");
            }
            else {
                message = JobsConfiguration.getInstance().getMessage("kill-info-no-sub");
            }
            if(message == null){
                message = "    %item%:%subitem% - %income%" + ChatColor.WHITE + ", %experience%" 
                    + ChatColor.YELLOW + " exp";
            }
            if(temp.getKey().contains(":")){
                message = message.replace("%item%", temp.getKey().split(":")[0].replace("org.bukkit.craftbukkit.entity.Craft", ""));
                message = message.replace("%subitem%", temp.getKey().split(":")[1]);
            }
            else{
                message = message.replace("%item%", temp.getKey().replace("org.bukkit.craftbukkit.entity.Craft", ""));
                message = message.replace(":%subitem%", "");
            }
            message = message.replace("%income%", format.format(incomeEquation.getValue()));
            message = message.replace("%experience%", format.format(expEquation.getValue()));
            Jobs.sendMessageByLine(sender, message);
        }
    }
    
    
    /**
     * Sends a message to line by line
     * @param sender - who receives info
     * @param message - message which needs to be sent
     */
    private static void sendMessageByLine(CommandSender sender, String message) {
        for(String line : message.split("\n")) {
            sender.sendMessage(line);
        }
    }
    
    /**
     * Sends the admin command success message
     * @param sender - who receives info
     */
    private static void sendAdminCommandSuccess(CommandSender sender) {
        String message = JobsConfiguration.getInstance().getMessage("admin-command-success");
        if(message == null){
            message = "Your command has been performed.";
        }
        Jobs.sendMessageByLine(sender, message);
    }

    /**
     * Sends the admin command failed message
     * @param sender - who receives info
     */
    private static void sendAdminCommandFailed(CommandSender sender) {
        String message = JobsConfiguration.getInstance().getMessage("admin-command-failed");
        if(message == null){
            message = ChatColor.RED + "There was an error in the command";
        }
        Jobs.sendMessageByLine(sender, message);
    }
	
	/**
	 * Add a player to the plugin to me managed.
	 * @param player
	 */
	public void addPlayer(Player player){
		players.put(player, new PlayerJobInfo(player, JobsConfiguration.getInstance().getJobsDAO()));
	}
	
	/**
	 * Remove a player from the plugin.
	 * @param player
	 */
	public void removePlayer(Player player){
		save(player);
		players.remove(player);
	}
	
	/**
	 * Get the playerJobInfo for the player
	 * @param player - the player you want the job info for
	 * @return the job info for the player
	 */
	public PlayerJobInfo getJob(Player player){
		return players.get(player);
	}
	
	/**
	 * Save all the information of all of the players in the game
	 */
	public void saveAll(){
		for(Player player: players.keySet()){
			save(player);
		}
	}
	
	/**
	 * Save the information for the specific player
	 * @param player - the player who's data is getting saved
	 */
	private void save(Player player){
		if(player != null){
			JobsConfiguration.getInstance().getJobsDAO().save(players.get(player));
		}
	}
	
	/**
	 * Get the player job info for specific player
	 * @param player - the player who's job you're getting
	 * @return the player job info of the player
	 */
	public PlayerJobInfo getPlayerJobInfo(Player player){
		return players.get(player);
	}
	
	/**
	 * Function to tell the player they do not have permission to do something
	 * @param sender
	 */
	private void noPermission(CommandSender sender){
		String tempMessage = JobsConfiguration.getInstance().getMessage("error-no-permission");
		if(tempMessage == null){
			sender.sendMessage(ChatColor.RED + "You do not have permission to do that");
		}
		else {
			for(String temp: tempMessage.split("\n")){
				sender.sendMessage(temp);
			}
		}
	}
	
	/**
	 * Function to tell the player that the job does not exist
	 * @param sender
	 */
	private void jobDoesNotExist(CommandSender sender){
		String tempMessage = JobsConfiguration.getInstance().getMessage("error-no-job");
		if(tempMessage == null){
			sender.sendMessage(ChatColor.RED + "The job you have selected does not exist");
		}
		else {
			for(String temp: tempMessage.split("\n")){
				sender.sendMessage(temp);
			}
		}
	}
	
	/**
	 * Get the current plugin
	 * @return a refference to the plugin
	 */
	private static Jobs getPlugin(){
		return plugin;
	}
	
	/**
	 * Disable the plugin
	 */
	public static void disablePlugin(){
		if(Jobs.getPlugin() != null){
			Jobs.getPlugin().getServer().getPluginManager().disablePlugin(Jobs.getPlugin());
		}
	}
	
	/**
	 * Get the server
	 * @return the server
	 */
	public static Server getJobsServer(){
		if(plugin != null){
			return plugin.getServer();
		}
		return null;
	}
}
