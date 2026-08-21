-- DEFENSIVE RLS POLICIES FOR THE MESSAGES TABLE
-- Ensure only authenticated users involved in a specific chat can read or insert messages.

-- 1. Enable RLS on the messages table
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

-- 2. Drop existing policies to avoid conflicts (if any)
DROP POLICY IF EXISTS "Users can view messages in their chats" ON public.messages;
DROP POLICY IF EXISTS "Users can insert messages into their chats" ON public.messages;

-- 3. Create a policy for SELECT (Read)
-- A user can read a message if their UID is one of the participantUids in the corresponding chat room.
-- Note: This assumes 'participantUids' is an array in the 'chats' table.
CREATE POLICY "Users can view messages in their chats" ON public.messages
FOR SELECT
USING (
  auth.uid()::text IN (
    SELECT UNNEST(participantUids)
    FROM public.chats
    WHERE id = public.messages.chatId
  )
);

-- 4. Create a policy for INSERT
-- A user can insert a message if they are the sender and are part of the chat.
CREATE POLICY "Users can insert messages into their chats" ON public.messages
FOR INSERT
WITH CHECK (
  auth.uid()::text = senderId AND
  auth.uid()::text IN (
    SELECT UNNEST(participantUids)
    FROM public.chats
    WHERE id = public.messages.chatId
  )
);

-- 5. Create a policy for UPDATE (Optional: for delivery status or editing)
CREATE POLICY "Users can update their own messages" ON public.messages
FOR UPDATE
USING (auth.uid()::text = senderId);

-- 6. Create a policy for DELETE (Optional: for retracting messages)
CREATE POLICY "Users can delete their own messages" ON public.messages
FOR DELETE
USING (auth.uid()::text = senderId);
