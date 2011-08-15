/**
 * Copyright (c) 2010, Sebastian Sdorra
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of SCM-Manager; nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * http://bitbucket.org/sdorra/scm-manager
 *
 */



package sonia.scm.web;

//~--- non-JDK imports --------------------------------------------------------

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sonia.scm.io.Command;
import sonia.scm.io.CommandResult;
import sonia.scm.io.SimpleCommand;
import sonia.scm.repository.GitRepositoryHandler;
import sonia.scm.repository.GitRepositoryHookEvent;
import sonia.scm.repository.RepositoryManager;
import sonia.scm.repository.RepositoryNotFoundException;
import sonia.scm.util.IOUtil;
import sonia.scm.util.Util;

//~--- JDK imports ------------------------------------------------------------

import java.io.File;
import java.io.IOException;

import java.util.Collection;

/**
 *
 * @author Sebastian Sdorra
 */
public class GitReceiveHook implements PreReceiveHook, PostReceiveHook
{

  /** Field description */
  public static final String FILE_HOOKDIRECTORY = "hooks";

  /** Field description */
  public static final String FILE_HOOK_POST_RECEIVE = "post-receive";

  /** Field description */
  public static final String FILE_HOOK_PRE_RECEIVE = "pre-receive";

  /** the logger for GitReceiveHook */
  private static final Logger logger =
    LoggerFactory.getLogger(GitReceiveHook.class);

  //~--- constructors ---------------------------------------------------------

  /**
   * Constructs ...
   *
   *
   * @param repositoryManager
   */
  public GitReceiveHook(RepositoryManager repositoryManager)
  {
    this.repositoryManager = repositoryManager;
  }

  //~--- methods --------------------------------------------------------------

  /**
   * Method description
   *
   *
   * @param rpack
   * @param receiveCommands
   */
  @Override
  public void onPostReceive(ReceivePack rpack,
                            Collection<ReceiveCommand> receiveCommands)
  {
    onReceive(rpack, receiveCommands, FILE_HOOK_POST_RECEIVE, true);
  }

  /**
   * Method description
   *
   *
   *
   * @param rpack
   * @param receiveCommands
   */
  @Override
  public void onPreReceive(ReceivePack rpack,
                           Collection<ReceiveCommand> receiveCommands)
  {
    onReceive(rpack, receiveCommands, FILE_HOOK_POST_RECEIVE, false);
  }

  /**
   * Method description
   *
   *
   * @param hook
   * @param oldId
   * @param newId
   * @param refName
   */
  private void executeFileHook(File hook, ObjectId oldId, ObjectId newId,
                               String refName)
  {
    final Command cmd = new SimpleCommand(hook.getAbsolutePath(), getId(oldId),
                          getId(newId), Util.nonNull(refName));

    try
    {
      CommandResult result = cmd.execute();

      if (result.isSuccessfull())
      {
        if (logger.isDebugEnabled())
        {
          logger.debug("executed file hook successfull");

          if (logger.isTraceEnabled())
          {
            String out = result.getOutput();

            if (Util.isNotEmpty(out))
            {
              logger.trace(out);
            }
          }
        }
      }
      else if (logger.isErrorEnabled())
      {
        logger.error("failed to execute file hook");

        String out = result.getOutput();

        if (Util.isNotEmpty(out))
        {
          logger.error(out);
        }
      }
    }
    catch (IOException ex)
    {
      logger.error("could not execute file hook", ex);
    }
  }

  /**
   * Method description
   *
   *
   * @param directory
   * @param oldId
   * @param newId
   */
  private void fireHookEvent(File directory, ObjectId oldId, ObjectId newId)
  {
    try
    {
      String repositoryName = directory.getName();
      GitRepositoryHookEvent e = new GitRepositoryHookEvent(directory, newId,
                                   oldId);

      repositoryManager.fireHookEvent(GitRepositoryHandler.TYPE_NAME,
                                      repositoryName, e);
    }
    catch (RepositoryNotFoundException ex)
    {
      logger.error("repository could not be found", ex);
    }
  }

  /**
   * Method description
   *
   *
   * @param rpack
   * @param receiveCommands
   * @param hook
   * @param fireApiHook
   */
  private void onReceive(ReceivePack rpack,
                         Collection<ReceiveCommand> receiveCommands,
                         String hook, boolean fireApiHook)
  {
    for (ReceiveCommand rc : receiveCommands)
    {
      if (rc.getResult() == ReceiveCommand.Result.OK)
      {
        ObjectId newId = rc.getNewId();
        ObjectId oldId = null;

        if (isUpdateCommand(rc))
        {
          oldId = rc.getOldId();
        }

        File directory = rpack.getRepository().getDirectory();
        File hookScript = getHookScript(directory, hook);

        if (hookScript != null)
        {
          executeFileHook(hookScript, oldId, newId, rc.getRefName());
        }

        if (fireApiHook)
        {
          fireHookEvent(directory, oldId, newId);
        }
      }
    }
  }

  //~--- get methods ----------------------------------------------------------

  /**
   * Method description
   *
   *
   * @param directory
   * @param name
   *
   * @return
   */
  private File getHookScript(File directory, String name)
  {
    File baseFile =
      new File(directory,
               FILE_HOOKDIRECTORY.concat(File.separator).concat(name));

    return IOUtil.getScript(baseFile);
  }

  /**
   * Method description
   *
   *
   * @param objectId
   *
   * @return
   */
  private String getId(ObjectId objectId)
  {
    String id = Util.EMPTY_STRING;

    if (objectId != null)
    {
      id = objectId.name();
    }

    return id;
  }

  /**
   * Method description
   *
   *
   * @param rc
   *
   * @return
   */
  private boolean isUpdateCommand(ReceiveCommand rc)
  {
    return (rc.getType() == ReceiveCommand.Type.UPDATE)
           || (rc.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
  }

  //~--- fields ---------------------------------------------------------------

  /** Field description */
  private RepositoryManager repositoryManager;
}