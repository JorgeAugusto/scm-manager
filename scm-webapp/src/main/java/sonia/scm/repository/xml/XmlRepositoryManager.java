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



package sonia.scm.repository.xml;

//~--- non-JDK imports --------------------------------------------------------

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sonia.scm.ConfigurationException;
import sonia.scm.HandlerEvent;
import sonia.scm.SCMContext;
import sonia.scm.SCMContextProvider;
import sonia.scm.StoreException;
import sonia.scm.Type;
import sonia.scm.repository.AbstractRepositoryManager;
import sonia.scm.repository.PermissionType;
import sonia.scm.repository.PermissionUtil;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryAllreadyExistExeption;
import sonia.scm.repository.RepositoryException;
import sonia.scm.repository.RepositoryHandler;
import sonia.scm.repository.RepositoryHandlerNotFoundException;
import sonia.scm.security.SecurityContext;
import sonia.scm.user.User;
import sonia.scm.util.AssertUtil;
import sonia.scm.util.IOUtil;

//~--- JDK imports ------------------------------------------------------------

import java.io.File;
import java.io.IOException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 *
 * @author Sebastian Sdorra
 */
@Singleton
public class XmlRepositoryManager extends AbstractRepositoryManager
{

  /** Field description */
  public static final String DATABASEFILE =
    "config".concat(File.separator).concat("repositories.xml");

  /** Field description */
  private static final Logger logger =
    LoggerFactory.getLogger(XmlRepositoryManager.class);

  //~--- constructors ---------------------------------------------------------

  /**
   * Constructs ...
   *
   *
   *
   * @param securityContextProvider
   * @param handlerSet
   */
  @Inject
  public XmlRepositoryManager(
          Provider<SecurityContext> securityContextProvider,
          Set<RepositoryHandler> handlerSet)
  {
    this.securityContextProvider = securityContextProvider;
    handlerMap = new HashMap<String, RepositoryHandler>();
    types = new HashSet<Type>();

    for (RepositoryHandler handler : handlerSet)
    {
      addHandler(handler);
    }

    try
    {
      JAXBContext context =
        JAXBContext.newInstance(XmlRepositoryDatabase.class);

      marshaller = context.createMarshaller();
      unmarshaller = context.createUnmarshaller();
    }
    catch (JAXBException ex)
    {
      throw new StoreException(ex);
    }
  }

  //~--- methods --------------------------------------------------------------

  /**
   * Method description
   *
   *
   * @throws IOException
   */
  @Override
  public void close() throws IOException
  {
    for (RepositoryHandler handler : handlerMap.values())
    {
      IOUtil.close(handler);
    }
  }

  /**
   * Method description
   *
   *
   * @param repository
   *
   * @throws IOException
   * @throws RepositoryException
   */
  @Override
  public void create(Repository repository)
          throws RepositoryException, IOException
  {
    if (logger.isInfoEnabled())
    {
      logger.info("create repository {} of type {}", repository.getName(),
                  repository.getType());
    }

    assertIsAdmin();
    AssertUtil.assertIsValid(repository);

    if (repositoryDB.contains(repository))
    {
      throw new RepositoryAllreadyExistExeption();
    }

    repository.setId(UUID.randomUUID().toString());
    repository.setCreationDate(System.currentTimeMillis());
    getHandler(repository).create(repository);

    synchronized (XmlRepositoryDatabase.class)
    {
      repositoryDB.add(repository.clone());
      storeDB();
    }

    fireEvent(repository, HandlerEvent.CREATE);
  }

  /**
   * Method description
   *
   *
   * @param repository
   *
   * @throws IOException
   * @throws RepositoryException
   */
  @Override
  public void delete(Repository repository)
          throws RepositoryException, IOException
  {
    if (logger.isInfoEnabled())
    {
      logger.info("delete repository {} of type {}", repository.getName(),
                  repository.getType());
    }

    assertIsOwner(repository);

    if (repositoryDB.contains(repository))
    {
      getHandler(repository).delete(repository);

      synchronized (XmlRepositoryDatabase.class)
      {
        repositoryDB.remove(repository);
        storeDB();
      }
    }
    else
    {
      throw new RepositoryException(
          "repository ".concat(repository.getName()).concat(" not found"));
    }

    fireEvent(repository, HandlerEvent.DELETE);
  }

  /**
   * Method description
   *
   *
   * @param context
   */
  @Override
  public void init(SCMContextProvider context)
  {
    File directory = context.getBaseDirectory();

    repositoryDBFile = new File(directory, DATABASEFILE);

    if (repositoryDBFile.exists())
    {
      loadDB();
    }
    else
    {
      IOUtil.mkdirs(repositoryDBFile.getParentFile());
      repositoryDB = new XmlRepositoryDatabase();
      repositoryDB.setCreationTime(System.currentTimeMillis());
    }
  }

  /**
   * Method description
   *
   *
   * @param repository
   *
   * @throws IOException
   * @throws RepositoryException
   */
  @Override
  public void modify(Repository repository)
          throws RepositoryException, IOException
  {
    if (logger.isInfoEnabled())
    {
      logger.info("modify repository {} of type {}", repository.getName(),
                  repository.getType());
    }

    AssertUtil.assertIsValid(repository);

    Repository notModifiedRepository = repositoryDB.get(repository.getType(),
                                         repository.getName());

    if (notModifiedRepository != null)
    {
      assertIsOwner(notModifiedRepository);
      getHandler(repository).modify(repository);
      repository.setLastModified(System.currentTimeMillis());

      synchronized (XmlRepositoryDatabase.class)
      {
        repositoryDB.remove(repository);
        repositoryDB.add(repository.clone());
        storeDB();
      }
    }
    else
    {
      throw new RepositoryException(
          "repository ".concat(repository.getName()).concat(" not found"));
    }

    fireEvent(repository, HandlerEvent.MODIFY);
  }

  /**
   * Method description
   *
   *
   * @param repository
   *
   * @throws IOException
   * @throws RepositoryException
   */
  @Override
  public void refresh(Repository repository)
          throws RepositoryException, IOException
  {
    AssertUtil.assertIsNotNull(repository);
    assertIsReader(repository);

    Repository fresh = repositoryDB.get(repository.getType(),
                         repository.getName());

    if (fresh != null)
    {
      fresh.copyProperties(repository);
    }
    else
    {
      throw new RepositoryException(
          "repository ".concat(repository.getName()).concat(" not found"));
    }
  }

  //~--- get methods ----------------------------------------------------------

  /**
   * Method description
   *
   *
   * @param id
   *
   * @return
   */
  @Override
  public Repository get(String id)
  {
    AssertUtil.assertIsNotEmpty(id);

    Repository repository = repositoryDB.get(id);

    if (repository != null)
    {
      assertIsReader(repository);
      repository = repository.clone();
    }

    return repository;
  }

  /**
   * Method description
   *
   *
   * @param type
   * @param name
   *
   * @return
   */
  @Override
  public Repository get(String type, String name)
  {
    AssertUtil.assertIsNotEmpty(type);
    AssertUtil.assertIsNotEmpty(name);

    Repository repository = repositoryDB.get(type, name);

    if (repository != null)
    {
      if (isReader(repository))
      {
        repository = repository.clone();
      }
      else
      {
        repository = null;
      }
    }

    return repository;
  }

  /**
   * Method description
   *
   *
   * @return
   */
  @Override
  public Collection<Repository> getAll()
  {
    LinkedList<Repository> repositories = new LinkedList<Repository>();

    for (Repository repository : repositoryDB.values())
    {
      if (isReader(repository))
      {
        repositories.add(repository.clone());
      }
    }

    return repositories;
  }

  /**
   * Method description
   *
   *
   * @param type
   *
   * @return
   */
  @Override
  public RepositoryHandler getHandler(String type)
  {
    return handlerMap.get(type);
  }

  /**
   * Method description
   *
   *
   * @return
   */
  @Override
  public Collection<Type> getTypes()
  {
    return types;
  }

  //~--- methods --------------------------------------------------------------

  /**
   * Method description
   *
   *
   * @param handler
   */
  private void addHandler(RepositoryHandler handler)
  {
    AssertUtil.assertIsNotNull(handler);

    Type type = handler.getType();

    AssertUtil.assertIsNotNull(type);

    if (handlerMap.containsKey(type.getName()))
    {
      throw new ConfigurationException(
          type.getName().concat("allready registered"));
    }

    if (logger.isInfoEnabled())
    {
      logger.info("added RepositoryHandler {} for type {}", handler.getClass(),
                  type);
    }

    handlerMap.put(type.getName(), handler);
    handler.init(SCMContext.getContext());
    types.add(type);
  }

  /**
   * Method description
   *
   *
   * @throws RepositoryException
   */
  private void assertIsAdmin() throws RepositoryException
  {
    if (!getCurrentUser().isAdmin())
    {
      throw new RepositoryException("admin permsission required");
    }
  }

  /**
   * Method description
   *
   *
   * @param repository
   */
  private void assertIsOwner(Repository repository)
  {
    PermissionUtil.assertPermission(repository, getCurrentUser(),
                                    PermissionType.OWNER);
  }

  /**
   * Method description
   *
   *
   * @param repository
   */
  private void assertIsReader(Repository repository)
  {
    PermissionUtil.assertPermission(repository, getCurrentUser(),
                                    PermissionType.READ);
  }

  /**
   * Method description
   *
   */
  private void loadDB()
  {
    try
    {
      repositoryDB =
        (XmlRepositoryDatabase) unmarshaller.unmarshal(repositoryDBFile);
    }
    catch (JAXBException ex)
    {
      throw new StoreException(ex);
    }
  }

  /**
   * Method description
   *
   */
  private void storeDB()
  {
    try
    {
      repositoryDB.setLastModified(System.currentTimeMillis());
      marshaller.marshal(repositoryDB, repositoryDBFile);
    }
    catch (JAXBException ex)
    {
      throw new StoreException(ex);
    }
  }

  //~--- get methods ----------------------------------------------------------

  /**
   * Method description
   *
   *
   * @return
   */
  private User getCurrentUser()
  {
    SecurityContext context = securityContextProvider.get();

    AssertUtil.assertIsNotNull(context);

    User user = context.getUser();

    AssertUtil.assertIsNotNull(user);

    return user;
  }

  /**
   * Method description
   *
   *
   * @param repository
   *
   * @return
   *
   *
   * @throws RepositoryException
   */
  private RepositoryHandler getHandler(Repository repository)
          throws RepositoryException
  {
    String type = repository.getType();
    RepositoryHandler handler = handlerMap.get(type);

    if (handler == null)
    {
      throw new RepositoryHandlerNotFoundException(
          "could not find handler for ".concat(type));
    }
    else if (!handler.isConfigured())
    {
      throw new RepositoryException("handler is not configured");
    }

    return handler;
  }

  /**
   * Method description
   *
   *
   * @param repository
   *
   * @return
   */
  private boolean isReader(Repository repository)
  {
    return PermissionUtil.hasPermission(repository, getCurrentUser(),
            PermissionType.READ);
  }

  //~--- fields ---------------------------------------------------------------

  /** Field description */
  private Map<String, RepositoryHandler> handlerMap;

  /** Field description */
  private Marshaller marshaller;

  /** Field description */
  private XmlRepositoryDatabase repositoryDB;

  /** Field description */
  private File repositoryDBFile;

  /** Field description */
  private Provider<SecurityContext> securityContextProvider;

  /** Field description */
  private Set<Type> types;

  /** Field description */
  private Unmarshaller unmarshaller;
}
