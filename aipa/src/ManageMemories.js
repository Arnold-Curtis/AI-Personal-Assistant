import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { toast } from 'react-toastify';
import './ManageMemories.css';

const ManageMemories = ({ user, onBack, darkMode }) => {
  const [memories, setMemories] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editingMemory, setEditingMemory] = useState(null);
  const [editingContent, setEditingContent] = useState('');
  const [newMemory, setNewMemory] = useState({ category: '', content: '' });
  const [showAddMemory, setShowAddMemory] = useState(false);
  const [filter, setFilter] = useState('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedMemories, setSelectedMemories] = useState(new Set());
  const [sortBy, setSortBy] = useState('updatedAt');
  const [sortOrder, setSortOrder] = useState('desc');
  const [isSearching, setIsSearching] = useState(false);

  useEffect(() => {
    loadMemoriesAndCategories();
  }, []);

  const loadMemoriesAndCategories = async () => {
    try {
      setLoading(true);
      
      const [memoriesResponse, categoriesResponse] = await Promise.all([
        axios.get('/api/memories'),
        axios.get('/api/memories/categories')
      ]);
      
      setMemories(memoriesResponse.data);
      setCategories(categoriesResponse.data);
    } catch (error) {
      toast.error('Failed to load memories: ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleEditMemory = (memory) => {
    setEditingMemory(memory);
    setEditingContent(memory.content);
  };

  const handleSaveEdit = async () => {
    if (!editingContent.trim()) {
      toast.error('Memory content cannot be empty');
      return;
    }

    try {
      await axios.put(`/api/memories/${editingMemory.id}`, {
        content: editingContent.trim()
      });
      
      toast.success('Memory updated successfully');
      setEditingMemory(null);
      setEditingContent('');
      loadMemoriesAndCategories();
    } catch (error) {
      toast.error('Failed to update memory');
    }
  };

  const handleDeleteMemory = async (memoryId) => {
    if (!window.confirm('Are you sure you want to delete this memory? This action cannot be undone.')) {
      return;
    }

    try {
      await axios.delete(`/api/memories/${memoryId}`);
      toast.success('Memory deleted successfully');
      loadMemoriesAndCategories();
    } catch (error) {
      toast.error('Failed to delete memory');
    }
  };

  const handleAddMemory = async () => {
    if (!newMemory.category.trim() || !newMemory.content.trim()) {
      toast.error('Both category and content are required');
      return;
    }

    try {
      await axios.post('/api/memories', {
        category: newMemory.category.trim(),
        content: newMemory.content.trim()
      });
      
      toast.success('Memory added successfully');
      setNewMemory({ category: '', content: '' });
      setShowAddMemory(false);
      loadMemoriesAndCategories();
    } catch (error) {
      toast.error('Failed to add memory: ' + (error.response?.data?.message || error.message));
    }
  };

  const handleSearch = async (searchQuery) => {
    if (!searchQuery.trim()) {
      loadMemoriesAndCategories();
      return;
    }

    try {
      setIsSearching(true);
      const response = await axios.get('/api/memories/search', {
        params: {
          query: searchQuery,
          category: filter !== 'all' ? filter : undefined
        }
      });
      setMemories(response.data);
    } catch (error) {
      toast.error('Failed to search memories');
    } finally {
      setIsSearching(false);
    }
  };

  const handleBulkDelete = async () => {
    if (selectedMemories.size === 0) {
      toast.error('No memories selected');
      return;
    }

    if (!window.confirm(`Are you sure you want to delete ${selectedMemories.size} selected memories? This action cannot be undone.`)) {
      return;
    }

    try {
      
      const memoryIds = Array.from(selectedMemories);
      let successCount = 0;
      
      for (const id of memoryIds) {
        try {
          await axios.delete(`/api/memories/${id}`);
          successCount++;
        } catch (error) {
          
        }
      }
      
      toast.success(`${successCount} of ${selectedMemories.size} memories deleted successfully`);
      setSelectedMemories(new Set());
      loadMemoriesAndCategories();
    } catch (error) {
      toast.error('Failed to delete memories');
    }
  };

  const handleSelectMemory = (memoryId) => {
    const newSelected = new Set(selectedMemories);
    if (newSelected.has(memoryId)) {
      newSelected.delete(memoryId);
    } else {
      newSelected.add(memoryId);
    }
    setSelectedMemories(newSelected);
  };

  const handleSelectAll = () => {
    if (selectedMemories.size === filteredMemories.length) {
      setSelectedMemories(new Set());
    } else {
      setSelectedMemories(new Set(filteredMemories.map(memory => memory.id)));
    }
  };

  const handleExportMemories = () => {
    const dataStr = JSON.stringify(memories, null, 2);
    const dataUri = 'data:application/json;charset=utf-8,'+ encodeURIComponent(dataStr);
    
    const exportFileDefaultName = `memories-export-${new Date().toISOString().split('T')[0]}.json`;
    
    const linkElement = document.createElement('a');
    linkElement.setAttribute('href', dataUri);
    linkElement.setAttribute('download', exportFileDefaultName);
    linkElement.click();
  };

  const getMemoryStats = () => {
    const stats = {
      total: memories.length,
      categories: categories.length,
      recentlyUpdated: memories.filter(m => {
        const updated = new Date(m.updatedAt);
        const weekAgo = new Date();
        weekAgo.setDate(weekAgo.getDate() - 7);
        return updated > weekAgo;
      }).length
    };
    return stats;
  };



  const sortMemories = (memoriesToSort) => {
    return [...memoriesToSort].sort((a, b) => {
      let aValue = a[sortBy];
      let bValue = b[sortBy];
      
      if (sortBy === 'createdAt' || sortBy === 'updatedAt') {
        aValue = new Date(aValue);
        bValue = new Date(bValue);
      }
      
      if (sortOrder === 'asc') {
        return aValue > bValue ? 1 : -1;
      } else {
        return aValue < bValue ? 1 : -1;
      }
    });
  };

  const filteredMemories = sortMemories(memories.filter(memory => {
    const matchesFilter = filter === 'all' || memory.category === filter;
    const matchesSearch = searchTerm === '' || 
      memory.content.toLowerCase().includes(searchTerm.toLowerCase()) ||
      memory.category.toLowerCase().includes(searchTerm.toLowerCase());
    
    return matchesFilter && matchesSearch;
  }));

  const formatDate = (dateString) => {
    try {
      return new Date(dateString).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return 'Unknown';
    }
  };

  if (loading) {
    return (
      <div className={`manage-memories-container ${darkMode ? 'dark-mode' : 'light-mode'}`}>
        <div className="loading-state">
          <div className="loading-spinner"></div>
          <p>Loading your memories...</p>
        </div>
      </div>
    );
  }

  return (
    <div className={`manage-memories-container ${darkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="manage-memories-header">
        <div className="header-top">
          <button 
            className="back-button"
            onClick={onBack}
            aria-label="Go back to My Account"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M19 12H5M12 19l-7-7 7-7" />
            </svg>
            Back to My Account
          </button>
        </div>
        
        <div className="header-main">
          <h1>Manage Memories</h1>
          <p>View and manage the information AI knows about you</p>
        </div>

        <div className="header-controls">
          <div className="search-filter-section">
            <div className="search-box">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="8"/>
                <path d="m21 21-4.35-4.35"/>
              </svg>
              <input
                type="text"
                placeholder="Search memories..."
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  if (e.target.value.trim()) {
                    handleSearch(e.target.value);
                  } else {
                    loadMemoriesAndCategories();
                  }
                }}
                className="search-input"
              />
              {isSearching && (
                <div className="search-loading">
                  <div className="spinner"></div>
                </div>
              )}
            </div>

            <select
              value={filter}
              onChange={(e) => {
                setFilter(e.target.value);
                if (searchTerm.trim()) {
                  handleSearch(searchTerm);
                }
              }}
              className="filter-select"
            >
              <option value="all">All Categories</option>
              {categories.map(category => (
                <option key={category} value={category}>{category}</option>
              ))}
            </select>

            <select
              value={`${sortBy}-${sortOrder}`}
              onChange={(e) => {
                const [newSortBy, newSortOrder] = e.target.value.split('-');
                setSortBy(newSortBy);
                setSortOrder(newSortOrder);
              }}
              className="sort-select"
            >
              <option value="updatedAt-desc">Latest Updated</option>
              <option value="updatedAt-asc">Oldest Updated</option>
              <option value="createdAt-desc">Recently Added</option>
              <option value="createdAt-asc">Oldest Added</option>
              <option value="category-asc">Category A-Z</option>
              <option value="category-desc">Category Z-A</option>
            </select>
          </div>

          <div className="action-buttons">
            {selectedMemories.size > 0 && (
              <div className="bulk-actions">
                <span className="selection-count">{selectedMemories.size} selected</span>
                <button
                  className="bulk-delete-button"
                  onClick={handleBulkDelete}
                  title="Delete selected memories"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="3,6 5,6 21,6"/>
                    <path d="M19,6v14a2,2 0 0,1-2,2H7a2,2 0 0,1-2-2V6m3,0V4a2,2 0 0,1,2-2h4a2,2 0 0,1,2,2v2"/>
                  </svg>
                  Delete Selected
                </button>
                <button
                  className="clear-selection-button"
                  onClick={() => setSelectedMemories(new Set())}
                >
                  Clear Selection
                </button>
              </div>
            )}
            
            <button
              className="add-memory-button"
              onClick={() => setShowAddMemory(true)}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              Add Memory
            </button>
            
            <button
              className="export-button"
              onClick={handleExportMemories}
              title="Export all memories as JSON"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7,10 12,15 17,10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              Export
            </button>
          </div>
        </div>
      </div>

      <div className="memories-content">
        {filteredMemories.length === 0 ? (
          <div className="empty-state">
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M9 12l2 2 4-4"/>
              <circle cx="12" cy="12" r="10"/>
            </svg>
            <h3>No memories found</h3>
            <p>
              {searchTerm || filter !== 'all' 
                ? 'Try adjusting your search or filter to find memories.'
                : 'Start by adding your first memory to help AI understand you better.'
              }
            </p>
            {!searchTerm && filter === 'all' && (
              <button
                className="add-first-memory-button"
                onClick={() => setShowAddMemory(true)}
              >
                Add Your First Memory
              </button>
            )}
          </div>
        ) : (
          <div className="memories-grid">
            <div className="memories-grid-header">
              <div className="select-all-section">
                <input
                  type="checkbox"
                  checked={selectedMemories.size === filteredMemories.length && filteredMemories.length > 0}
                  onChange={handleSelectAll}
                  className="select-all-checkbox"
                />
                <label>Select All ({filteredMemories.length})</label>
              </div>
            </div>
            {filteredMemories.map((memory) => (
              <div key={memory.id} className={`memory-card ${selectedMemories.has(memory.id) ? 'selected' : ''}`}>
                <div className="memory-header">
                  <div className="memory-header-left">
                    <input
                      type="checkbox"
                      checked={selectedMemories.has(memory.id)}
                      onChange={() => handleSelectMemory(memory.id)}
                      className="memory-checkbox"
                    />
                    <span className="memory-category">{memory.category}</span>
                  </div>
                  <div className="memory-actions">
                    <button
                      className="edit-memory-button"
                      onClick={() => handleEditMemory(memory)}
                      title="Edit memory"
                    >
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                      </svg>
                    </button>
                    <button
                      className="delete-memory-button"
                      onClick={() => handleDeleteMemory(memory.id)}
                      title="Delete memory"
                    >
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <polyline points="3,6 5,6 21,6"/>
                        <path d="M19,6v14a2,2 0 0,1-2,2H7a2,2 0 0,1-2-2V6m3,0V4a2,2 0 0,1,2-2h4a2,2 0 0,1,2,2v2"/>
                        <line x1="10" y1="11" x2="10" y2="17"/>
                        <line x1="14" y1="11" x2="14" y2="17"/>
                      </svg>
                    </button>
                  </div>
                </div>
                
                <div className="memory-content">
                  {editingMemory?.id === memory.id ? (
                    <div className="editing-content">
                      <textarea
                        value={editingContent}
                        onChange={(e) => setEditingContent(e.target.value)}
                        className="edit-textarea"
                        rows="4"
                        autoFocus
                      />
                      <div className="edit-actions">
                        <button
                          className="cancel-edit-button"
                          onClick={() => {
                            setEditingMemory(null);
                            setEditingContent('');
                          }}
                        >
                          Cancel
                        </button>
                        <button
                          className="save-edit-button"
                          onClick={handleSaveEdit}
                        >
                          Save
                        </button>
                      </div>
                    </div>
                  ) : (
                    <p className="memory-text">{memory.content}</p>
                  )}
                </div>
                
                <div className="memory-footer">
                  <span className="memory-date">
                    Added: {formatDate(memory.createdAt)}
                  </span>
                  {memory.updatedAt !== memory.createdAt && (
                    <span className="memory-updated">
                      Updated: {formatDate(memory.updatedAt)}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {}
      {showAddMemory && (
        <div className="modal-overlay">
          <div className="modal">
            <h3>Add New Memory</h3>
            <p>Add information you'd like the AI to remember about you</p>
            
            <div className="form-group">
              <label htmlFor="memory-category">Category</label>
              <input
                type="text"
                id="memory-category"
                value={newMemory.category}
                onChange={(e) => setNewMemory(prev => ({ ...prev, category: e.target.value }))}
                placeholder="e.g., Personal, Preferences, Goals"
                className="modal-input"
                list="category-suggestions"
              />
              <datalist id="category-suggestions">
                {categories.map(category => (
                  <option key={category} value={category} />
                ))}
                <option value="Personal" />
                <option value="Preferences" />
                <option value="Goals" />
                <option value="Family" />
                <option value="Work" />
                <option value="Health" />
                <option value="Hobbies" />
              </datalist>
            </div>

            <div className="form-group">
              <label htmlFor="memory-content">Memory Content</label>
              <textarea
                id="memory-content"
                value={newMemory.content}
                onChange={(e) => setNewMemory(prev => ({ ...prev, content: e.target.value }))}
                placeholder="What would you like the AI to remember? (e.g., 'I prefer coffee over tea', 'My birthday is March 15th')"
                className="modal-textarea"
                rows="4"
              />
            </div>

            <div className="modal-actions">
              <button
                onClick={() => {
                  setShowAddMemory(false);
                  setNewMemory({ category: '', content: '' });
                }}
                className="modal-cancel-button"
              >
                Cancel
              </button>
              <button
                onClick={handleAddMemory}
                className="modal-confirm-button"
                disabled={!newMemory.category.trim() || !newMemory.content.trim()}
              >
                Add Memory
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="memories-footer">
        <div className="stats-section">
          <div className="stat-item">
            <span className="stat-number">{getMemoryStats().total}</span>
            <span className="stat-label">Total Memories</span>
          </div>
          <div className="stat-item">
            <span className="stat-number">{getMemoryStats().categories}</span>
            <span className="stat-label">Categories</span>
          </div>
          <div className="stat-item">
            <span className="stat-number">{filteredMemories.length}</span>
            <span className="stat-label">Showing</span>
          </div>
          <div className="stat-item">
            <span className="stat-number">{getMemoryStats().recentlyUpdated}</span>
            <span className="stat-label">Updated This Week</span>
          </div>
        </div>
        
        <div className="footer-actions">
          <button className="home-button" onClick={() => window.location.href = '/'}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9,22 9,12 15,12 15,22"/>
            </svg>
            Back to Home
          </button>
        </div>
      </div>
    </div>
  );
};

export default ManageMemories;

