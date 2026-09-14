import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "cn"
import { Slot } from "radix-ui"

const buttonVariants = cva(
  "group/button inline-flex shrink-0 items-center justify-center border border-transparent bg-clip-padding text-sm font-semibold whitespace-nowrap transition-all outline-none select-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/50 active:not-aria-[haspopup]:translate-y-px disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-2 aria-invalid:ring-destructive/20 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground hover:bg-primary-active disabled:bg-primary-disabled disabled:text-[#707a8a]",
        outline:
          "border-border bg-background text-foreground hover:bg-muted aria-expanded:bg-muted aria-expanded:text-foreground dark:border-input dark:bg-input/30 dark:hover:bg-input/50",
        secondary:
          "bg-background text-ink border border-[#eaecef] hover:bg-[#fafafa] dark:bg-[#1e2329] dark:text-[#ffffff] dark:border-transparent dark:hover:bg-[#2b3139]",
        ghost:
          "hover:bg-muted hover:text-foreground aria-expanded:bg-muted aria-expanded:text-foreground dark:hover:bg-muted/50",
        destructive:
          "bg-destructive/10 text-destructive hover:bg-destructive/20 focus-visible:border-destructive/40 focus-visible:ring-destructive/20 dark:bg-destructive/20 dark:hover:bg-destructive/30",
        link: "text-primary underline-offset-4 hover:underline",
        "trading-up": "bg-[#0ecb81] text-[#ffffff] hover:bg-[#0ecb81]/90",
        "trading-down": "bg-[#f6465d] text-[#ffffff] hover:bg-[#f6465d]/90",
        tertiary: "bg-transparent hover:underline text-foreground",
      },
      size: {
        default: "h-10 px-6 rounded-md",
        pill: "h-12 px-8 rounded-pill",
        sm: "h-8 px-5 rounded-sm text-xs",
        lg: "h-12 px-8 rounded-md",
        icon: "size-10 rounded-md",
        "icon-sm": "size-8 rounded-sm",
        "icon-lg": "size-12 rounded-md",
        subscribe: "h-7 px-3 rounded-md text-xs",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

function Button({
  className,
  variant = "default",
  size = "default",
  asChild = false,
  ...props
}: React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }) {
  const Comp = asChild ? Slot.Root : "button"

  return (
    <Comp
      data-slot="button"
      data-variant={variant}
      data-size={size}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }
