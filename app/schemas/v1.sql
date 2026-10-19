-- Pre-release version 1 baseline. Never shipped; used to validate the 1 -> 2 upgrade.
-- UUIDs/enums are TEXT, local dates are epoch days, timestamps are epoch milliseconds.
CREATE TABLE IF NOT EXISTS categories (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, color INTEGER NOT NULL, icon TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT, scheduledDate INTEGER NOT NULL, startAt INTEGER, endAt INTEGER, allDay INTEGER NOT NULL, priority TEXT NOT NULL, completed INTEGER NOT NULL, categoryId TEXT, recurrenceRule TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL);
CREATE INDEX IF NOT EXISTS index_tasks_scheduledDate_completed ON tasks(scheduledDate, completed);
CREATE INDEX IF NOT EXISTS index_tasks_categoryId ON tasks(categoryId);
CREATE TABLE IF NOT EXISTS reminders (id TEXT NOT NULL PRIMARY KEY, taskId TEXT, title TEXT, description TEXT, triggerAt INTEGER NOT NULL, enabled INTEGER NOT NULL, recurrenceRule TEXT, soundUri TEXT, soundEnabled INTEGER NOT NULL, vibrationEnabled INTEGER NOT NULL, FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE INDEX IF NOT EXISTS index_reminders_taskId ON reminders(taskId);
CREATE INDEX IF NOT EXISTS index_reminders_enabled_triggerAt ON reminders(enabled, triggerAt);
CREATE TABLE IF NOT EXISTS subtasks (id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, title TEXT NOT NULL, completed INTEGER NOT NULL, sortOrder INTEGER NOT NULL, FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE INDEX IF NOT EXISTS index_subtasks_taskId_sortOrder ON subtasks(taskId, sortOrder);
CREATE TABLE IF NOT EXISTS focus_sessions (id TEXT NOT NULL PRIMARY KEY, taskId TEXT, startedAt INTEGER NOT NULL, endedAt INTEGER, durationSeconds INTEGER NOT NULL, completed INTEGER NOT NULL, FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE SET NULL);
CREATE INDEX IF NOT EXISTS index_focus_sessions_taskId ON focus_sessions(taskId);
CREATE INDEX IF NOT EXISTS index_focus_sessions_startedAt ON focus_sessions(startedAt);
