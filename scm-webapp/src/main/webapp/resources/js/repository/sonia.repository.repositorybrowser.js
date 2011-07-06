/* *
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

Sonia.repository.RepositoryBrowser = Ext.extend(Ext.grid.GridPanel, {
  
  repository: null,
  revision: null,
  
  repositoryBrowserTitleText: 'Source: {0}',
  
  iconFolder: 'resources/images/folder.gif',
  iconDocument: 'resources/images/document.gif',
  templateIcon: '<img src="{0}" alt="{1}" title="{2}" />',
  templateLink: '<a class="scm-browser" rel="{1}" href="#">{0}</a>',

  initComponent: function(){
    
    if (debug){
      console.debug('create new browser for repository ' + this.repository.name + " and revision " + this.revision);
    }
    
    var browserStore = new Sonia.rest.JsonStore({
      proxy: new Ext.data.HttpProxy({
        url: restUrl + 'repositories/' + this.repository.id  + '/browse.json',
        method: 'GET'
      }),
      fields: ['path', 'name', 'length', 'lastModified', 'directory', 'description'],
      root: 'files',
      idProperty: 'path',
      autoLoad: true,
      autoDestroy: true
    });
    
    // register listener
    browserStore.addListener('load', this.loadStore, this);
    
    if ( this.revision ){
      browserStore.baseParams = {
        revision: this.revision
      };
    }
    
    var browserColModel = new Ext.grid.ColumnModel({
      defaults: {
        sortable: false
      },
      columns: [{
        id: 'icon',
        dataIndex: 'directory',
        header: '',
        width: 28,
        renderer: this.renderIcon,
        scope: this
      },{
        id: 'name',
        dataIndex: 'name',
        header: 'Name',
        renderer: this.renderName,
        scope: this,
        width: 180
      },{
        id: 'length',
        dataIndex: 'length',
        header: 'Length',
        renderer: this.renderLength
      },{
        id: 'lastModified',
        dataIndex: 'lastModified',
        header: 'Last Modified',
        renderer: Ext.util.Format.formatTimestamp
      },{
        id: 'description',
        dataIndex: 'description',
        header: 'Description'
      }]
    });
    
    var bar = [this.createFolderButton('', '')];
    this.appendRepositoryProperties(bar);
    
    var config = {
      bbar: bar,
      autoExpandColumn: 'description',
      title: String.format(this.repositoryBrowserTitleText, this.repository.name),
      store: browserStore,
      colModel: browserColModel,
      loadMask: true,
      listeners: {
        click: {
          fn: this.onClick,
          scope: this
        }
      }
    };
    
    Ext.apply(this, Ext.apply(this.initialConfig, config));
    Sonia.repository.RepositoryBrowser.superclass.initComponent.apply(this, arguments);
  },
  
  loadStore: function(store, records, extra){
    var path = extra.params.path;
    if ( path != null && path.length > 0 ){
      
      var index = path.lastIndexOf('/');
      if ( index > 0 ){
        path = path.substr(0, index);
      } else {
        path = '';
      }
      
      var File = Ext.data.Record.create([{
          name: 'name'
        },{
          name: 'path'
        },{
          name: 'directory'
        },{
          name: 'length'
        },{
          name: 'lastModified'
      }]);
    
      store.insert(0, new File({
        name: '..',
        path: path,
        directory: true,
        length: 0
      }));
    }
  },
  
  onClick: function(e){
    var el = e.getTarget('.scm-browser');
    
    if ( el != null ){
    
      var rel = el.rel.split(':');
      var path = rel[1];

      if ( rel[0] == 'dir' ){
        this.changeDirectory(path);
      } else {
        this.openFile(path);
      }
    }
  },
  
  getName: function(path){
    var name = path;
    var index = path.lastIndexOf('/');
    if ( index > 0 ){
      name = path.substr(index +1);
    }
    return name
  },
  
  getExtension: function(path){
    var ext = null;
    var index = path.lastIndexOf('.');
    if ( index > 0 ){
      ext = path.substr(index + 1, path.length);
    }
    return ext;
  },
  
  appendRepositoryProperties: function(bar){
    bar.push('->',this.repository.name);
    if ( this.revision != null ){
      bar.push(': ', this.revision);
    }
  },
  
  openFile: function(path){
    if ( debug ){
      console.debug( 'open file: ' + path );
    }
    
    var ext = this.getExtension( path );    
    
    var url = restUrl + 'repositories/' + this.repository.id  + '/content?path=' + path;
    if ( this.revision ){
      url += "&revision=" + this.revision;
    }
    
    var bar = [path];
    this.appendRepositoryProperties(bar);
    
    main.addTab({
      id: this.repository.id + "-b-"  + path,
      contentUrl: url,
      xtype: 'syntaxHighlighterPanel',
      title: this.getName(path),
      closable: true,
      autoScroll: true,
      syntax: ext,
      bbar: bar
    });
  },
  
  changeDirectory: function(path){
    if ( path.substr(-1) === '/' ){
      path = path.substr( 0, path.length - 1 );
    }
    
    if (debug){
      console.debug( 'change directory: ' + path );
    }
    
    this.getStore().load({
      params: {
        path: path
      }
    });
    
    this.renderClickPath(path);
  },
  
  createFolderButton: function(path, name){
    return {
      xtype: 'button',
      icon: this.iconFolder,
      text: name + '/',
      handler: this.changeDirectory.createDelegate(this, [path])
    };
  },
  
  renderClickPath: function(path){
    var bbar = this.getBottomToolbar();
    bbar.removeAll();
    
    var parts = path.split('/');
    var currentPath = '';
    var items = [this.createFolderButton(currentPath, '')];
          
    if ( path != '' ){
      for (var i=0; i<parts.length; i++){
        currentPath += parts[i] + '/';
        items.push(this.createFolderButton(currentPath, parts[i]));
      }
    }
    
    items.push('->', this.repository.name);
    if ( this.revision != null ){
      items.push(':', this.revision);
    }
    
    bbar.add(items);
    bbar.doLayout();
  },
  
  renderName: function(name, p, record){
    var path = record.data.directory ? 'dir:' : 'file:';
    path += record.data.path;
    return String.format(this.templateLink, name, path);
  },
  
  renderIcon: function(directory, p, record){
    var icon = null;
    var name = record.data.name;
    if ( directory ){
      icon = this.iconFolder;
    } else {
      icon = this.iconDocument;
    }
    return String.format(this.templateIcon, icon, name, name);
  },
  
  renderLength: function(length, p, record){
    var result = '';
    var directory = record.data.directory;
    if ( ! directory ){
      result = Ext.util.Format.fileSize(length);
    }
    return result;
  }

});

// register xtype
Ext.reg('repositoryBrowser', Sonia.repository.RepositoryBrowser);
