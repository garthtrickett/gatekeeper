        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SyncError.NetworkFailure(e.message ?: "Unknown network failure").left()
        }
    }
}
    suspend fun pullChanges(lastSyncTimestamp: Long = 0L): Either<SyncError, SyncPullPayload> {
