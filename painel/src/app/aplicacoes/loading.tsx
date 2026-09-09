import { Skeleton } from "@/components/ui/skeleton";

export default function Loading() {
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-6 py-10">
      <Skeleton className="h-4 w-32" />
      <Skeleton className="h-8 w-64" />
      <Skeleton className="h-10 w-44" />
      <div className="flex flex-col gap-2">
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} className="h-12 w-full" />
        ))}
      </div>
    </main>
  );
}
