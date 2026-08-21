-- ================================================================
-- DATABASE SCHEMA: Supabase users_data Table
-- Run this script in the Supabase SQL Editor
-- ================================================================

-- Create the users_data table that references auth.users(id)
CREATE TABLE IF NOT EXISTS public.users_data (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    login_count INT4 NOT NULL DEFAULT 0
);

-- Enable Row Level Security (RLS) on users_data table
ALTER TABLE public.users_data ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY "Enable read access for authenticated users to their own data"
ON public.users_data FOR SELECT
TO authenticated
USING (auth.uid() = id);

CREATE POLICY "Enable update access for users to their own data"
ON public.users_data FOR UPDATE
TO authenticated
USING (auth.uid() = id)
WITH CHECK (auth.uid() = id);

CREATE POLICY "Enable insert access for users to their own data"
ON public.users_data FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = id);

-- Secure Postgres function to increment login_count safely without exposing SQL logic
CREATE OR REPLACE FUNCTION public.increment_login_count(target_user_id UUID)
RETURNS int4 AS $$
DECLARE
    new_count int4;
BEGIN
    UPDATE public.users_data
    SET login_count = login_count + 1
    WHERE id = target_user_id
    RETURNING login_count INTO new_count;
    
    RETURN new_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
