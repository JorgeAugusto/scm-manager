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



package sonia.scm.repository.api;

//~--- non-JDK imports --------------------------------------------------------

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import sonia.scm.cache.Cache;
import sonia.scm.cache.CacheManager;
import sonia.scm.repository.BlameResult;
import sonia.scm.repository.Repository;
import sonia.scm.repository.spi.BlameCommand;
import sonia.scm.repository.spi.BlameCommandRequest;

//~--- JDK imports ------------------------------------------------------------

import java.io.Serializable;

/**
 * Shows changeset information by line for a given file.
 * Blame is also known as annotate in some SCM systems.
 *
 * @author Sebastian Sdorra
 * @since 1.17
 */
public final class BlameCommandBuilder
{

  /** name of the cache */
  private static final String CACHE_NAME = "sonia.scm.cache.blame";

  //~--- constructors ---------------------------------------------------------

  /**
   * Constructs a new {@link BlameCommandBuilder}, this constructor should
   * only be called from the {@link RepositoryService}.
   *
   * @param cacheManager cache manager
   * @param blameCommand implementation of the {@link BlameCommand}
   * @param repository repository to query
   */
  BlameCommandBuilder(CacheManager cacheManager, BlameCommand blameCommand,
                      Repository repository)
  {
    this.cache = cacheManager.getCache(CacheKey.class, BlameResult.class,
                                       CACHE_NAME);
    this.blameCommand = blameCommand;
    this.repository = repository;
  }

  //~--- methods --------------------------------------------------------------

  /**
   * Reset each parameter to its default value.
   *
   * @return {@code this}
   */
  public BlameCommandBuilder reset()
  {
    request.reset();

    return this;
  }

  //~--- get methods ----------------------------------------------------------

  /**
   * Returns changeset informations by line for the given file.
   *
   * @param path path of the file
   * @return changeset informations by line for the given file
   *
   * @throws IllegalArgumentException if the path is null or empty
   */
  public BlameResult getBlameResult(String path)
  {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(path),
                                "path is required");

    BlameCommandRequest requestClone = request.clone();

    requestClone.setPath(path);

    BlameResult result = null;

    if (disableCache)
    {
      result = blameCommand.getBlameResult(requestClone);
    }
    else
    {
      CacheKey key = new CacheKey(repository, request);

      result = cache.get(key);

      if (result == null)
      {
        result = blameCommand.getBlameResult(requestClone);

        if (result != null)
        {
          cache.put(key, result);
        }
      }
    }

    return result;
  }

  //~--- set methods ----------------------------------------------------------

  /**
   * Disables the cache. This means that every {@link BlameResult}
   * is directly retrieved from the {@link Repository}. <b>Note: </b> Disabling
   * the cache cost a lot of performance and could be much more slower.
   *
   *
   * @param disableCache true to disable the cache
   *
   * @return {@code this}
   */
  public BlameCommandBuilder setDisableCache(boolean disableCache)
  {
    this.disableCache = disableCache;

    return this;
  }

  /**
   * Sets the start revision for the blame.
   *
   *
   * @param revision revision to start from
   *
   * @return {@code this}
   */
  public BlameCommandBuilder setRevision(String revision)
  {
    request.setRevision(revision);

    return this;
  }

  //~--- inner classes --------------------------------------------------------

  /**
   * Key for cache.
   *
   *
   * @version        Enter version here..., 12/06/05
   * @author         Enter your name here...
   */
  private static class CacheKey implements Serializable
  {

    /**
     * Constructs ...
     *
     *
     * @param repository
     * @param request
     */
    public CacheKey(Repository repository, BlameCommandRequest request)
    {
      this.repositoryId = repository.getId();
      this.request = request;
    }

    //~--- methods ------------------------------------------------------------

    /**
     * Method description
     *
     *
     * @param obj
     *
     * @return
     */
    @Override
    public boolean equals(Object obj)
    {
      if (obj == null)
      {
        return false;
      }

      if (getClass() != obj.getClass())
      {
        return false;
      }

      final CacheKey other = (CacheKey) obj;

      return Objects.equal(repositoryId, other.repositoryId)
             && Objects.equal(request, other.request);
    }

    /**
     * Method description
     *
     *
     * @return
     */
    @Override
    public int hashCode()
    {
      return Objects.hashCode(repositoryId, request);
    }

    //~--- fields -------------------------------------------------------------

    /** Field description */
    private String repositoryId;

    /** Field description */
    private BlameCommandRequest request;
  }


  //~--- fields ---------------------------------------------------------------

  /** implementation of the blame command */
  private BlameCommand blameCommand;

  /** the cache */
  private Cache<CacheKey, BlameResult> cache;

  /** disable change */
  private boolean disableCache;

  /** the repository */
  private Repository repository;

  /** request for the blame command implementation */
  private BlameCommandRequest request = new BlameCommandRequest();
}